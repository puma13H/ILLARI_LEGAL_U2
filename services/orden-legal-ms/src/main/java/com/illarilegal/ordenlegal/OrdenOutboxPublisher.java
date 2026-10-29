package com.illarilegal.ordenlegal;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import io.micrometer.core.instrument.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** One bounded transaction per row; a failed message cannot starve the following rows. */
@Component
public class OrdenOutboxPublisher {
    private final OrdenOutboxRepository outbox;
    private final KafkaTemplate<String,String> kafka;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    public OrdenOutboxPublisher(OrdenOutboxRepository outbox, KafkaTemplate<String,String> kafka,
            PlatformTransactionManager manager, MeterRegistry metrics) {
        this.outbox=outbox; this.kafka=kafka; this.metrics=metrics;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Gauge.builder("illari.outbox.pendientes",outbox,OrdenOutboxRepository::contarPendientes).tag("servicio","orden").register(metrics);
        Gauge.builder("illari.outbox.fallidos",outbox,OrdenOutboxRepository::countByFallidoTrue).tag("servicio","orden").register(metrics);
        Gauge.builder("illari.outbox.edad.segundos",outbox,r->{
            var oldest=r.masAntiguoPendiente();
            return oldest==null?0:Math.max(0,java.time.Duration.between(oldest,Instant.now()).toSeconds());
        }).tag("servicio","orden").register(metrics);
        for(String metric:java.util.List.of("publicados","reintentados","agotados"))
            metrics.counter("illari.outbox."+metric,"servicio","orden");
    }
    @Scheduled(fixedDelayString="${illari.outbox.delay-ms:1200}")
    public void publicarPendientes() {
        for(Long id:outbox.pendientes(Instant.now(),PageRequest.of(0,20))) {
            try { tx.executeWithoutResult(status->outbox.bloquear(id).ifPresent(e->{
                if(!e.listo(Instant.now()))return;
                try {
                    e.prepararEnvio();
                    var envelope=EventEnvelope.parse(e.getPayload());
                    try(var scope=new Correlation(envelope.path("traceId").asText(null),e.getEventId())) {
                    kafka.send("illari.orden.creada",e.getOrdenId().toString(),e.getPayload()).get(2,TimeUnit.SECONDS);
                    e.setPublicado(true);
                    ObservationCommit.log("outbox_published");
                    metrics.counter("illari.outbox.publicados","servicio","orden").increment();
                    }
                } catch(InterruptedException error) {
                    Thread.currentThread().interrupt(); e.fallo(error);
                    metrics.counter("illari.outbox.reintentados","servicio","orden").increment();
                } catch(Exception error) {
                    e.fallo(error);
                    try(var scope=new Correlation(EventEnvelope.parse(e.getPayload()).path("traceId").asText(null),e.getEventId())) {
                        org.slf4j.LoggerFactory.getLogger("ILLARI_OBS").warn("outbox_retry attempts={} exhausted={}",e.getIntentos(),e.isFallido());
                    }
                    metrics.counter("illari.outbox.reintentados","servicio","orden").increment();
                    if(e.isFallido()) metrics.counter("illari.outbox.agotados","servicio","orden").increment();
                }
                outbox.save(e);
            })); } catch(org.springframework.dao.DataAccessException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("outbox_transaccion_fallida id={} causa={}",id,failure.getClass().getSimpleName());
            }
            if(Thread.currentThread().isInterrupted())return;
        }
    }
}
