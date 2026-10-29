package com.illarilegal.pago;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class OrdenEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrdenEventConsumer.class);

    private final PagoService pagos;
    private final ObjectMapper objectMapper;
    private final PagoOutboxRepository outbox;
    private final EventoRecibidoRepository recibidos;

    @org.springframework.beans.factory.annotation.Autowired
    public OrdenEventConsumer(PagoService pagos, ObjectMapper objectMapper, PagoOutboxRepository outbox, EventoRecibidoRepository recibidos) {
        this.pagos = pagos; this.objectMapper = objectMapper;
        this.outbox = outbox;
        this.recibidos = recibidos;
    }

    @Transactional
    @KafkaListener(topics = "illari.orden.creada", groupId = "illari-pago-ms")
    public void onOrden(String payload) {
        var json=EventEnvelope.parse(payload);
        String eventId=EventEnvelope.id(json), hash=EventEnvelope.hash(json);
        try(var scope=new Correlation(json.path("traceId").asText(null),eventId)) {
        var previousEvent=recibidos.findById(eventId);
        if(previousEvent.isPresent()){previousEvent.get().verificar(hash);return;}
        final OrdenCreadaEvento evento;
        try {
            evento = objectMapper.readValue(payload, OrdenCreadaEvento.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("No se pudo leer el evento de orden creada.", exception);
        }
        if (evento == null || evento.ordenId() == null || evento.ordenId() < 1
                || evento.idCliente() == null || evento.idCliente().isBlank()
                || evento.total() == null || evento.total().signum() <= 0
                || evento.total().stripTrailingZeros().scale() > 2
                || evento.metodoPago() == null || evento.metodoPago().isBlank()
                || !"orden-legal-ms".equals(evento.origen())) {
            throw new IllegalArgumentException("El evento de orden requiere ordenId, cliente, total y metodoPago.");
        }

        String metodo=evento.metodoPago().trim().toUpperCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("YAPE","PLIN","BIPAY","TARJETA","TARJETA_RECHAZADA").contains(metodo))
            throw new IllegalArgumentException("Método de pago no admitido.");
        PagoService.Pago pago;
        if ("orden.cancelacion.solicitada".equals(evento.tipoEvento())) {
            pago = pagos.cancelarDesdeOrden(evento.ordenId(), evento.idCliente(), evento.total());
        } else if ("orden.creada".equals(evento.tipoEvento())) {
            pago = pagos.iniciarDesdeOrden(evento.ordenId(), evento.idCliente(), evento.total());
            pago = pagos.resolver(pago.id(), !"TARJETA_RECHAZADA".equals(metodo));
        } else {
            throw new IllegalArgumentException("Comando de orden desconocido.");
        }
        // Publish the actual durable state, never a state recomputed from a replay's requested method.
        String estado = pago.estado();
        String tipoEvento = switch (estado) {
            case "VALIDADO" -> "pago.validado";
            case "FALLIDO" -> "pago.fallido";
            case "CANCELADO" -> "pago.cancelado";
            case "REEMBOLSADO" -> "pago.reembolsado";
            default -> throw new IllegalStateException("Pago sin resultado publicable.");
        };
        ResultadoPagoEvento resultado = new ResultadoPagoEvento(
                tipoEvento, pago.idOrden(), pago.idCliente(), pago.monto(), estado,
                "pago-ms", Instant.now(), pago.id(), pago.revision());
        try {
            String eventJson = objectMapper.writeValueAsString(resultado);
            var existing = outbox.findForUpdate(evento.ordenId());
            if (existing.isEmpty()) {
                outbox.save(new PagoOutboxEntity(evento.ordenId(), eventJson));
            } else {
                var previous = objectMapper.readTree(existing.get().getPayload());
                if (!estado.equals(previous.path("estado").asText())
                        || previous.path("pagoId").asLong() != pago.id()
                        || previous.path("revisionPago").asLong() != pago.revision()) {
                    existing.get().actualizarResultado(eventJson);
                    outbox.save(existing.get());
                }
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo guardar el evento de resultado de pago", exception);
        }
        logger.info("saga_pago_procesado ordenId={} estado={}", evento.ordenId(), estado);
        recibidos.saveAndFlush(new EventoRecibidoEntity(eventId,hash));
        ObservationCommit.log("kafka_processed");
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record OrdenCreadaEvento(
            String tipoEvento,
            Long ordenId,
            String idCliente,
            BigDecimal total,
            String metodoPago,
            String origen,
            Instant timestamp) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultadoPagoEvento(
            String tipoEvento,
            Long ordenId,
            String idCliente,
            BigDecimal monto,
            String estado,
            String origen,
            Instant timestamp,
            Long pagoId,
            Long revisionPago) {}
}
