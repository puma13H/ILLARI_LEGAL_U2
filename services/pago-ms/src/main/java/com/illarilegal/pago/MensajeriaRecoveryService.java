package com.illarilegal.pago;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import io.micrometer.core.instrument.*;
import java.time.Instant;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MensajeriaRecoveryService {
    public static final String INPUT="illari.orden.creada";
    private final MensajeFallidoRepository repo;
    private final KafkaTemplate<String,String> kafka;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    private final ReplayAuditoriaRepository auditoria;
    public MensajeriaRecoveryService(MensajeFallidoRepository repo,KafkaTemplate<String,String> kafka,
            PlatformTransactionManager manager,MeterRegistry metrics,ReplayAuditoriaRepository auditoria) {
        this.repo=repo;this.kafka=kafka;tx=new TransactionTemplate(manager);this.metrics=metrics;
        this.auditoria=auditoria;
        Gauge.builder("illari.kafka.dlt.pendientes",repo,r->r.countByEstado("DLT")+r.countByEstado("REPLAY_FALLIDO"))
                .tag("servicio","pago").register(metrics);
        Gauge.builder("illari.kafka.replay.pendientes",repo,r->r.countByEstado("REPLAY_PENDIENTE"))
                .tag("servicio","pago").register(metrics);
    }
    @Transactional
    @KafkaListener(topics="illari.orden.creada.DLT",groupId="illari-pago-dlt",containerFactory="deadLetterContainerFactory")
    public void capturar(ConsumerRecord<String,String> record) {
        var offsetHeader=record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET);
        long offset=offsetHeader==null?record.offset():ByteBuffer.wrap(offsetHeader.value()).getLong();
        var partHeader=record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION);
        int partition=partHeader==null?record.partition():ByteBuffer.wrap(partHeader.value()).getInt();
        var timeHeader=record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TIMESTAMP);
        long originalTime=timeHeader==null?record.timestamp():ByteBuffer.wrap(timeHeader.value()).getLong();
        String originalPayload=record.value()==null?"null":record.value();
        // Coordinates can be reused after topic recreation. Redelivery of the SAME original stays stable.
        String id=UUID.nameUUIDFromBytes((INPUT+":"+partition+":"+offset+":"+originalTime+":"+originalPayload)
                .getBytes(StandardCharsets.UTF_8)).toString();
        var error=record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN);
        if(!repo.existsById(id)) repo.saveAndFlush(new MensajeFallidoEntity(id,INPUT,partition,offset,originalPayload,
                error==null?"KafkaError":new String(error.value(),StandardCharsets.UTF_8)));
    }
    public record Vista(String id,String topic,int particion,long offset,String estado,int reprocesos,int intentos,
                        String errorTipo,Instant creado,String operador,String motivo) {}
    private Vista vista(MensajeFallidoEntity m){return new Vista(m.id,m.topic,m.particion,m.offsetOriginal,m.estado,m.reprocesos,m.intentos,m.errorTipo,m.creado,m.operador,m.motivo);}
    public java.util.List<Vista> listar(){return listar(0,100);}
    public java.util.List<Vista> listar(int page,int size){
        if(page<0 || size<1 || size>100)throw new IllegalArgumentException("Paginación inválida.");
        return repo.findAll(org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by("creado").descending()))
                .stream().map(this::vista).toList();
    }
    public record Detalle(Vista resumen,String payloadOriginal,String payloadReplay){}
    public Detalle detalle(String id) {
        var m=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new Detalle(vista(m),m.payload,m.payloadReplay);
    }
    @Transactional
    public Vista solicitarReplay(String id,String corregido,String motivo,String operador) {
        if(operador==null || operador.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(motivo==null || motivo.isBlank() || motivo.length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica el motivo (máximo 500 caracteres).");
        var m=repo.bloquear(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if("REPLAY_PENDIENTE".equals(m.estado))return vista(m);
        if(m.reprocesos>=3)throw new ResponseStatusException(HttpStatus.CONFLICT,"Límite de reprocesos alcanzado.");
        String candidate=corregido==null?m.payload:corregido;
        if(candidate.length()>1048576)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Evento demasiado grande.");
        var candidateJson=EventEnvelope.parse(candidate);
        if(!candidateJson.hasNonNull("traceId")) {
            try { candidateJson.put("traceId",EventEnvelope.parse(m.payload).path("traceId").asText(Correlation.normalize(Correlation.current()))); }
            catch(IllegalArgumentException ignored) { candidateJson.put("traceId",Correlation.normalize(Correlation.current())); }
        }
        candidate=candidateJson.toString();
        if(!INPUT.equals(m.topic))throw new IllegalStateException("Destino de replay no autorizado.");
        m.payloadReplay=candidate;m.operador=operador;m.motivo=motivo;m.solicitado=Instant.now();
        m.proximoIntento=Instant.now();m.intentos=0;m.reprocesos++;m.estado="REPLAY_PENDIENTE";
        repo.save(m);auditar("DLT_REPLAY",m.id,operador,motivo,candidate);return vista(m);
    }
    @Transactional public void auditar(String tipo,String referencia,String operador,String motivo,String payload) {
        auditoria.save(new ReplayAuditoriaEntity(tipo,referencia,operador,motivo,payload));
    }
    public java.util.List<ReplayAuditoriaEntity.Vista> operaciones() {
        return auditoria.findAll(org.springframework.data.domain.PageRequest.of(0,100,org.springframework.data.domain.Sort.by("id").descending()))
                .stream().map(ReplayAuditoriaEntity::vista).toList();
    }
    @Scheduled(fixedDelayString="${illari.outbox.delay-ms:1200}")
    public void publicarReplays() {
        for(String id:repo.pendientes(Instant.now(),PageRequest.of(0,20))) {
            try {tx.executeWithoutResult(s->repo.bloquear(id).ifPresent(m->{
                if(!"REPLAY_PENDIENTE".equals(m.estado) || (m.proximoIntento!=null && m.proximoIntento.isAfter(Instant.now())))return;
                try {
                    kafka.send(INPUT,EventEnvelope.parse(m.payloadReplay).path("ordenId").asText(),m.payloadReplay).get(2,TimeUnit.SECONDS);
                    try(var scope=new Correlation(EventEnvelope.parse(m.payloadReplay).path("traceId").asText(null),EventEnvelope.id(EventEnvelope.parse(m.payloadReplay)))) {
                        ObservationCommit.log("kafka_replay_sent");
                    }
                    m.estado="REPLAY_ENVIADO";metrics.counter("illari.kafka.reprocesados","servicio","pago").increment();
                }catch(Exception e) {
                    if(e instanceof InterruptedException)Thread.currentThread().interrupt();
                    m.intentos++;m.proximoIntento=Instant.now().plusSeconds(Math.min(60,1L<<Math.min(m.intentos,6)));
                    if(m.intentos>=5)m.estado="REPLAY_FALLIDO";
                }
                repo.save(m);
            }));}catch(org.springframework.dao.DataAccessException e){
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("replay_transaccion_fallida id={}",id);
            }
            if(Thread.currentThread().isInterrupted())return;
        }
    }
}

