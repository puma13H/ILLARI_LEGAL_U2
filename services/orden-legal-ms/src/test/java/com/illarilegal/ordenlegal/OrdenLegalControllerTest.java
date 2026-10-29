package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrdenLegalControllerTest {
    private static final String SUBJECT = "keycloak-user-uuid-7f93";

    private OrdenOutboxRepository outbox;
    private OrdenRepository repository;
    private CatalogoResilienteService catalog;
    private OrdenLegalController controller;

    @BeforeEach
    void setUp() {
        outbox = mock(OrdenOutboxRepository.class);
        repository = mock(OrdenRepository.class);
        catalog = mock(CatalogoResilienteService.class);
        controller = new OrdenLegalController(objectMapper(), repository, catalog, outbox);
        when(repository.save(any(OrdenEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(catalog.obtener(7L))
                .thenReturn(new OrdenLegalController.ServicioLegal(
                        7L, "Asesoría legal", "Consulta", new BigDecimal("50.00"), "ASESORIA", "ACTIVO"));
    }

    @Test
    void createsPendingOrderAndPublishesTheKeycloakSubject() throws Exception {
        when(repository.save(any(OrdenEntity.class))).thenAnswer(call -> {
            OrdenEntity entity = call.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 17L);
            return entity;
        });

        OrdenLegalController.OrdenLegal order = controller.crear(
                new OrdenLegalController.NuevaOrden(
                        "YAPE",
                        List.of(new OrdenLegalController.DetalleNuevaOrden(7L, 2))),
                jwt(SUBJECT));

        ArgumentCaptor<OrdenEntity> savedOrder = ArgumentCaptor.forClass(OrdenEntity.class);
        verify(repository).save(savedOrder.capture());
        assertEquals(SUBJECT, savedOrder.getValue().getIdCliente());
        assertEquals("PENDIENTE_PAGO", savedOrder.getValue().getEstado());
        assertEquals(new BigDecimal("100.00"), savedOrder.getValue().getTotal());
        assertEquals(SUBJECT, order.idCliente());
        assertEquals(17L, order.id());

        ArgumentCaptor<OrdenOutboxEntity> eventJson = ArgumentCaptor.forClass(OrdenOutboxEntity.class);
        verify(outbox).save(eventJson.capture());
        OrdenLegalController.OrdenCreadaEvento event = objectMapper().readValue(
                eventJson.getValue().getPayload(), OrdenLegalController.OrdenCreadaEvento.class);
        assertEquals(SUBJECT, event.idCliente());
        assertEquals(order.id(), event.ordenId());
    }

    @Test
    void listsOrdersByTheAuthenticatedKeycloakSubjectWithoutNumericConversion() {
        when(repository.findByIdClienteOrderByFechaCreacionDesc(SUBJECT)).thenReturn(List.of());

        var response = controller.porCliente(SUBJECT, jwt(SUBJECT));

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
        verify(repository).findByIdClienteOrderByFechaCreacionDesc(SUBJECT);
    }

    @Test
    void rejectsListingAnotherCustomersOrders() {
        var response = controller.porCliente("some-other-subject", jwt(SUBJECT));

        assertEquals(403, response.getStatusCode().value());
        assertFalse(response.hasBody());
    }

    @Test
    void mapsApprovedAndRejectedPaymentEventsToOrderStates() {
        OrdenEntity approved = order();
        OrdenEntity rejected = order();
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(approved));
        when(repository.findForUpdate(2L)).thenReturn(Optional.of(rejected));

        controller.registrarResultadoPago(result(1L, "VALIDADO"));
        controller.registrarResultadoPago(result(2L, "FALLIDO"));

        assertEquals("PAGADA", approved.getEstado());
        assertEquals("CANCELADA", rejected.getEstado());
        verify(repository).save(approved);
        verify(repository).save(rejected);
    }

    @Test
    void savesOrderWithTwoServicesAndOneDurableOutboxEvent() {
        OrdenOutboxRepository outbox = mock(OrdenOutboxRepository.class);
        CatalogoResilienteService service = mock(CatalogoResilienteService.class);
        when(service.obtener(7L)).thenReturn(new OrdenLegalController.ServicioLegal(
                7L, "Asesoría", "", new BigDecimal("50.00"), "ASESORIA", "ACTIVO"));
        when(service.obtener(8L)).thenReturn(new OrdenLegalController.ServicioLegal(
                8L, "Documentos", "", new BigDecimal("20.00"), "DIGITAL", "ACTIVO"));
        when(repository.save(any(OrdenEntity.class))).thenAnswer(call -> {
            OrdenEntity entity = call.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 81L);
            return entity;
        });
        OrdenLegalController integrated = new OrdenLegalController(
                objectMapper(), repository, service, outbox);
        var order = integrated.crear(new OrdenLegalController.NuevaOrden("YAPE", List.of(
                new OrdenLegalController.DetalleNuevaOrden(7L, 2),
                new OrdenLegalController.DetalleNuevaOrden(8L, 1))), jwt(SUBJECT));
        assertEquals(new BigDecimal("120.00"), order.total());
        assertEquals(2, order.detalles().size());
        verify(outbox).save(any(OrdenOutboxEntity.class));
    }

    @Test
    void rejectsContradictoryReplayAfterSagaTerminalState() {
        OrdenEntity paid = order(); paid.setEstado("PAGADA");
        when(repository.findForUpdate(91L)).thenReturn(Optional.of(paid));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.registrarResultadoPago(result(91L, "FALLIDO")));
        assertEquals("PAGADA", paid.getEstado());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(paid);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of("CLIENTE")))
                .build();
    }

    private static PagoEventConsumer.PagoResultadoEvento result(Long id, String state) {
        return new PagoEventConsumer.PagoResultadoEvento("VALIDADO".equals(state) ? "pago.validado" : "pago.fallido",
                id, SUBJECT, BigDecimal.TEN, state, "pago-ms", Instant.now(), id + 100, 1L);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private static OrdenEntity order() {
        return new OrdenEntity(
                SUBJECT, Instant.now(), "PENDIENTE_PAGO", BigDecimal.TEN, "TARJETA", "[]");
    }
}
