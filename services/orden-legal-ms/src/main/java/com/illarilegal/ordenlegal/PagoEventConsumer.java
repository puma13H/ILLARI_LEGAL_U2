package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class PagoEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(PagoEventConsumer.class);

    private final OrdenLegalController ordenes;
    private final ObjectMapper objectMapper;
    private final EventoRecibidoRepository recibidos;

    public PagoEventConsumer(OrdenLegalController ordenes, ObjectMapper objectMapper, EventoRecibidoRepository recibidos) {
        this.ordenes = ordenes;
        this.objectMapper = objectMapper;
        this.recibidos = recibidos;
    }

    @KafkaListener(topics = "illari.pago.resultado", groupId = "illari-orden-legal-ms")
    @org.springframework.transaction.annotation.Transactional
    public void onPago(String payload) {
        var json=EventEnvelope.parse(payload);
        String eventId=EventEnvelope.id(json), hash=EventEnvelope.hash(json);
        try(var scope=new Correlation(json.path("traceId").asText(null),eventId)) {
        var previous=recibidos.findById(eventId);
        if(previous.isPresent()){previous.get().verificar(hash);return;}
        try {
            PagoResultadoEvento evento = objectMapper.readValue(payload, PagoResultadoEvento.class);
            if (evento == null) throw new IllegalArgumentException("El resultado de pago no puede ser nulo.");
            evento.validarContrato();
            ordenes.registrarResultadoPago(evento);
            recibidos.saveAndFlush(new EventoRecibidoEntity(eventId,hash));
        ObservationCommit.log("kafka_processed");
            logger.info("Resultado de pago aplicado a orden {}: {}", evento.ordenId(), evento.estado());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("No se pudo leer el evento de resultado de pago.", exception);
        }
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record PagoResultadoEvento(
            String tipoEvento,
            Long ordenId,
            String idCliente,
            BigDecimal monto,
            String estado,
            String origen,
            Instant timestamp,
            Long pagoId,
            Long revisionPago) {
        public void validarContrato() {
            if (ordenId == null || ordenId < 1 || pagoId == null || pagoId < 1
                    || idCliente == null || idCliente.isBlank() || monto == null || monto.signum() <= 0
                    || timestamp == null || !"pago-ms".equals(origen) || estado == null || revisionPago == null) {
                throw new IllegalArgumentException("Resultado de pago incompleto o inválido.");
            }
            String tipoEsperado = switch (estado) {
                case "VALIDADO" -> "pago.validado";
                case "FALLIDO" -> "pago.fallido";
                case "CANCELADO" -> "pago.cancelado";
                case "REEMBOLSADO" -> "pago.reembolsado";
                default -> throw new IllegalArgumentException("Estado de pago desconocido.");
            };
            long revisionEsperada = ("CANCELADO".equals(estado) || "REEMBOLSADO".equals(estado)) ? 2L : 1L;
            if (!tipoEsperado.equals(tipoEvento) || revisionPago != revisionEsperada) {
                throw new IllegalArgumentException("Tipo o revisión incompatible con el resultado de pago.");
            }
        }
    }
}
