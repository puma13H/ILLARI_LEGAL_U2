package com.illarilegal.pago;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrdenEventConsumerTest {
    private PagoService pagos;
    private PagoOutboxRepository outbox;
    private OrdenEventConsumer consumer;

    @BeforeEach
    void setUp() {
        pagos = mock(PagoService.class);
        outbox = mock(PagoOutboxRepository.class);
        consumer = new OrdenEventConsumer(pagos, objectMapper(), outbox, mock(EventoRecibidoRepository.class));
    }

    @Test
    void publishesApprovedPaymentResultForOrderEvent() throws Exception {
        when(pagos.iniciarDesdeOrden(11L, "keycloak-sub-11", new BigDecimal("45.00")))
                .thenReturn(pago(11L, "PENDIENTE"));
        when(pagos.resolver(3L, true)).thenReturn(pago(11L, "VALIDADO"));

        consumer.onOrden(orderEvent(11L, "keycloak-sub-11", "YAPE"));

        verify(pagos).resolver(3L, true);
        assertPublishedResult(11L, "keycloak-sub-11", "VALIDADO", "pago.validado");
    }

    @Test
    void publishesRejectedPaymentResultForRejectedTestMethod() throws Exception {
        when(pagos.iniciarDesdeOrden(12L, "keycloak-sub-12", new BigDecimal("45.00")))
                .thenReturn(pago(12L, "PENDIENTE"));
        when(pagos.resolver(3L, false)).thenReturn(pago(12L, "FALLIDO"));

        consumer.onOrden(orderEvent(12L, "keycloak-sub-12", "TARJETA_RECHAZADA"));

        verify(pagos).resolver(3L, false);
        assertPublishedResult(12L, "keycloak-sub-12", "FALLIDO", "pago.fallido");
    }

    private void assertPublishedResult(Long orderId, String subject, String state, String eventType) throws Exception {
        ArgumentCaptor<PagoOutboxEntity> payload = ArgumentCaptor.forClass(PagoOutboxEntity.class);
        verify(outbox).save(payload.capture());
        OrdenEventConsumer.ResultadoPagoEvento result = objectMapper().readValue(
                payload.getValue().getPayload(), OrdenEventConsumer.ResultadoPagoEvento.class);
        assertEquals(orderId, result.ordenId());
        assertEquals(subject, result.idCliente());
        assertEquals(state, result.estado());
        assertEquals(eventType, result.tipoEvento());
    }

    private static String orderEvent(Long id, String subject, String method) {
        return "{\"tipoEvento\":\"orden.creada\",\"ordenId\":" + id
                + ",\"idCliente\":\"" + subject + "\",\"total\":45.00"
                + ",\"metodoPago\":\"" + method + "\",\"origen\":\"orden-legal-ms\"}";
    }

    private static PagoService.Pago pago(Long idOrden, String estado) {
        return new PagoService.Pago(3L, idOrden, "keycloak-sub-" + idOrden, new BigDecimal("45.00"),
                estado, null, null);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
