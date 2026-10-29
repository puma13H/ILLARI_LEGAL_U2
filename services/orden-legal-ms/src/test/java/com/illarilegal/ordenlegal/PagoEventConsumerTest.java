package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PagoEventConsumerTest {
    @Test
    void appliesKafkaApprovedAndRejectedResultsToTheMatchingOrders() {
        OrdenLegalController orders = mock(OrdenLegalController.class);
        PagoEventConsumer consumer = new PagoEventConsumer(orders, new ObjectMapper().findAndRegisterModules(), mock(EventoRecibidoRepository.class));

        var approved = result(31L, "VALIDADO", "pago.validado");
        var failed = result(32L, "FALLIDO", "pago.fallido");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        try {
            consumer.onPago(mapper.writeValueAsString(approved));
            consumer.onPago(mapper.writeValueAsString(failed));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new AssertionError(e); }
        verify(orders).registrarResultadoPago(approved);
        verify(orders).registrarResultadoPago(failed);
    }

    private static PagoEventConsumer.PagoResultadoEvento result(Long order, String state, String type) {
        return new PagoEventConsumer.PagoResultadoEvento(type, order, "client", java.math.BigDecimal.TEN,
                state, "pago-ms", java.time.Instant.parse("2026-10-27T00:00:00Z"), order + 100, 1L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ordenId", "pagoId", "idCliente", "monto", "estado",
            "origen", "tipoEvento", "revisionPago", "timestamp", "json"})
    void incompleteOrContradictoryContractsCannotReachOrderTransitions(String field) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(result(31L, "VALIDADO", "pago.validado"));
        switch (field) {
            case "ordenId", "pagoId", "monto" -> tree.put(field, 0);
            case "idCliente" -> tree.put(field, "");
            case "estado", "origen", "tipoEvento" -> tree.put(field, "incorrecto");
            case "revisionPago" -> tree.put(field, 2);
            case "timestamp" -> tree.remove(field);
            default -> { }
        }
        OrdenLegalController orders = mock(OrdenLegalController.class);
        String payload = "json".equals(field) ? "{invalid" : mapper.writeValueAsString(tree);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PagoEventConsumer(orders, mapper, mock(EventoRecibidoRepository.class)).onPago(payload));
        org.mockito.Mockito.verifyNoInteractions(orders);
    }
}
