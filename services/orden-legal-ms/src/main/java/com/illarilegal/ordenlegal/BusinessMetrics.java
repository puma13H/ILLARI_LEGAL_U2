package com.illarilegal.ordenlegal;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.*;
@Component
public class BusinessMetrics {
    public BusinessMetrics(OrdenRepository repo,EventoRecibidoRepository inbox,MeterRegistry metrics) {
        Gauge.builder("illari.ordenes.registradas",repo,r->r.count()).register(metrics);
        for(String state:java.util.List.of("PENDIENTE_PAGO","PAGADA","CANCELACION_PENDIENTE","CANCELADA"))
            Gauge.builder("illari.ordenes.estado",repo,r->r.countByEstado(state)).tag("estado",state).register(metrics);
        Gauge.builder("illari.kafka.procesados",inbox,r->r.count()).tag("servicio","orden").register(metrics);
    }
}
