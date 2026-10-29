package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrdenConsistenciaRegressionTest {
    OrdenRepository repo;
    OrdenOutboxRepository outbox;
    CatalogoResilienteService catalogo;
    OrdenLegalController controller;
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setup() {
        repo = mock(OrdenRepository.class);
        outbox = mock(OrdenOutboxRepository.class);
        catalogo = mock(CatalogoResilienteService.class);
        controller = new OrdenLegalController(mapper, repo, catalogo, outbox);
        when(repo.save(any(OrdenEntity.class))).thenAnswer(i -> {
            OrdenEntity e = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(e, "id", 1L);
            return e;
        });
    }

    @Test void cancellationMustWaitForPaymentCompensation() {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        assertThat(controller.estado(1L, Map.of("estado", "CANCELADA")).getBody().estado())
                .isEqualTo("CANCELACION_PENDIENTE");
        verify(outbox).save(any(OrdenOutboxEntity.class));
    }

    @Test void zeroTotalCannotPersistAnOrder() { rejectsPrice(BigDecimal.ZERO); }
    @Test void negativePriceCannotPersistAnOrder() { rejectsPrice(new BigDecimal("-1.00")); }

    private void rejectsPrice(BigDecimal price) {
        when(catalogo.obtener(7L)).thenReturn(new OrdenLegalController.ServicioLegal(
                7L, "Servicio", "", price, "LEGAL", "ACTIVO"));
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("client").build();
        assertThatThrownBy(() -> controller.crear(new OrdenLegalController.NuevaOrden("YAPE",
                List.of(new OrdenLegalController.DetalleNuevaOrden(7L, 1))), jwt))
                .isInstanceOf(ResponseStatusException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test void paymentFromAnotherCustomerIsRejected() {
        rejectsResult("other", "80.00");
    }

    @Test void paymentForAnotherAmountIsRejected() {
        rejectsResult("client", "1.00");
    }

    private void rejectsResult(String client, String amount) {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        String event = "{\"tipoEvento\":\"pago.validado\",\"ordenId\":1,\"idCliente\":\"" + client
                + "\",\"monto\":" + amount + ",\"estado\":\"VALIDADO\",\"origen\":\"pago-ms\","
                + "\"pagoId\":101,\"revisionPago\":1,\"timestamp\":\"2026-10-27T00:00:00Z\"}";
        assertThatThrownBy(() -> new PagoEventConsumer(controller, mapper, mock(EventoRecibidoRepository.class)).onPago(event))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getEstado()).isEqualTo("PENDIENTE_PAGO");
        verify(repo, never()).save(any());
    }

    static OrdenEntity pending() {
        OrdenEntity order = new OrdenEntity("client", Instant.now(), "PENDIENTE_PAGO", new BigDecimal("80.00"), "YAPE", "[]");
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    @Test void cancellationWaitsForRefundAndThenToleratesStaleAndDuplicateResults() {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        controller.estado(1L, Map.of("estado", "CANCELADA"));
        controller.estado(1L, Map.of("estado", "CANCELADA"));
        controller.registrarResultadoPago(result("VALIDADO", 101L));
        assertThat(order.getEstado()).isEqualTo("CANCELACION_PENDIENTE");
        controller.registrarResultadoPago(result("REEMBOLSADO", 101L));
        assertThat(order.getEstado()).isEqualTo("CANCELADA");
        controller.registrarResultadoPago(result("VALIDADO", 101L));
        controller.registrarResultadoPago(result("REEMBOLSADO", 101L));
        assertThat(order.getEstado()).isEqualTo("CANCELADA");
        assertThat(order.getEstadoPago()).isEqualTo("REEMBOLSADO");
        verify(outbox, times(1)).save(any());
    }

    @Test void lateApprovalOfLegacyCancelledOrderRequestsCompensationInsteadOfIgnoringIt() {
        OrdenEntity order = pending(); order.setEstado("CANCELADA");
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        controller.registrarResultadoPago(result("VALIDADO", 101L));
        assertThat(order.getEstado()).isEqualTo("CANCELACION_PENDIENTE");
        verify(outbox).save(any());
        controller.registrarResultadoPago(result("REEMBOLSADO", 101L));
        assertThat(order.getEstado()).isEqualTo("CANCELADA");
    }

    @Test void anApprovedOrderCanOnlyBeCancelledAfterRefundAcknowledgement() {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        controller.registrarResultadoPago(result("VALIDADO", 101L));
        assertThat(order.getEstado()).isEqualTo("PAGADA");
        controller.estado(1L, Map.of("estado", "CANCELADA"));
        assertThat(order.getEstado()).isEqualTo("CANCELACION_PENDIENTE");
        controller.registrarResultadoPago(result("REEMBOLSADO", 101L));
        assertThat(order.getEstado()).isEqualTo("CANCELADA");
    }

    @Test void anotherPaymentCannotReplaceTheRegisteredPayment() {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        controller.registrarResultadoPago(result("VALIDADO", 101L));
        assertThatThrownBy(() -> controller.registrarResultadoPago(result("VALIDADO", 102L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getPagoId()).isEqualTo(101L);
    }

    @Test void aPaymentIdAlreadyLinkedToAnotherOrderIsRejected() {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        when(repo.existsByPagoIdAndIdNot(101L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> controller.registrarResultadoPago(result("VALIDADO", 101L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getEstado()).isEqualTo("PENDIENTE_PAGO");
    }

    @Test void unknownOrderAndUnsolicitedRefundAreRejected() {
        assertThatThrownBy(() -> controller.registrarResultadoPago(result("VALIDADO", 101L)))
                .isInstanceOf(IllegalArgumentException.class);
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> controller.registrarResultadoPago(result("REEMBOLSADO", 101L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getEstado()).isEqualTo("PENDIENTE_PAGO");
    }

    @Test void contradictorySameRevisionIsRejected() {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        controller.registrarResultadoPago(result("VALIDADO", 101L));
        assertThatThrownBy(() -> controller.registrarResultadoPago(result("FALLIDO", 101L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getEstado()).isEqualTo("PAGADA");
    }

    @Test void equalAmountsWithDifferentDecimalScaleAreAccepted() {
        OrdenEntity order = pending();
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(order));
        var good = result("VALIDADO", 101L);
        controller.registrarResultadoPago(new PagoEventConsumer.PagoResultadoEvento(good.tipoEvento(), 1L,
                "client", new BigDecimal("80.0"), good.estado(), good.origen(), good.timestamp(), 101L, 1L));
        assertThat(order.getEstado()).isEqualTo("PAGADA");
    }

    static PagoEventConsumer.PagoResultadoEvento result(String state, Long paymentId) {
        String type = switch (state) {
            case "VALIDADO" -> "pago.validado";
            case "FALLIDO" -> "pago.fallido";
            case "CANCELADO" -> "pago.cancelado";
            case "REEMBOLSADO" -> "pago.reembolsado";
            default -> "unknown";
        };
        return new PagoEventConsumer.PagoResultadoEvento(type, 1L, "client", new BigDecimal("80.00"),
                state, "pago-ms", Instant.now(), paymentId,
                List.of("CANCELADO", "REEMBOLSADO").contains(state) ? 2L : 1L);
    }
}
