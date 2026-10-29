package com.illarilegal.pago;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.*;
@Component
public class BusinessMetrics {
    public BusinessMetrics(PagoRepository repo,EventoRecibidoRepository inbox,MeterRegistry metrics) {
        Gauge.builder("illari.pagos.registradas",repo,r->r.count()).register(metrics);
        for(String state:java.util.List.of("PENDIENTE","VALIDADO","FALLIDO","CANCELADO","REEMBOLSADO"))
            Gauge.builder("illari.pagos.estado",repo,r->r.countByEstado(state)).tag("estado",state).register(metrics);
        Gauge.builder("illari.kafka.procesados",inbox,r->r.count()).tag("servicio","pago").register(metrics);
    }
}
