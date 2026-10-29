package com.illarilegal.pago;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pago_outbox", uniqueConstraints=@UniqueConstraint(name="uk_pago_outbox_event",columnNames="event_id"))
public class PagoOutboxEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "orden_id", nullable = false, unique = true) private Long ordenId;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false) private boolean publicado;
    @Column(nullable = false) private Instant creado;
    protected PagoOutboxEntity() {}
    private String eventId;
    private Integer intentos = 0;
    private java.time.Instant proximoIntento;
    private Boolean fallido = false;
    private String ultimoError;
    private String ultimoOperador;
    @Column(length=500) private String motivoReintento;
    public Long idEvento() { return id; }
    public String getEventId() { return eventId; }
    public int getIntentos() { return intentos == null ? 0 : intentos; }
    public boolean isFallido() { return Boolean.TRUE.equals(fallido); }
    public java.time.Instant getProximoIntento() { return proximoIntento; }
    public boolean listo(java.time.Instant ahora) {
        return !publicado && !isFallido() && (proximoIntento == null || !proximoIntento.isAfter(ahora));
    }
    public void prepararEnvio() {
        if(eventId!=null) {
            var json=EventEnvelope.parse(payload);json.put("eventId",eventId);payload=json.toString();
        }
        payload=EventEnvelope.enrich(payload);
        eventId=EventEnvelope.id(EventEnvelope.parse(payload));
    }
    public void fallo(Throwable error) {
        intentos=getIntentos()+1; ultimoError=error.getClass().getSimpleName();
        fallido=intentos>=5;
        proximoIntento=java.time.Instant.now().plusMillis(Math.min(60000,250L << Math.min(intentos-1,8)));
    }
    public void reintentar(String operador, String motivo) {
        if(publicado) throw new IllegalArgumentException("El evento ya fue publicado.");
        intentos=0; fallido=false; proximoIntento=java.time.Instant.now();
        ultimoOperador=operador; motivoReintento=motivo;
    }
    public PagoOutboxEntity(Long ordenId, String payload) {
        this.ordenId = ordenId; this.payload = EventEnvelope.enrich(payload); this.creado = Instant.now();
        this.eventId=EventEnvelope.id(EventEnvelope.parse(this.payload));
    }
    public Long getOrdenId() { return ordenId; }
    public String getPayload() { return payload; }
    public boolean isPublicado() { return publicado; }
    public void setPublicado(boolean publicado) { this.publicado = publicado; }
    public void actualizarResultado(String payload) {
        this.payload = EventEnvelope.enrich(payload);
        this.publicado = false;
        this.eventId=EventEnvelope.id(EventEnvelope.parse(this.payload)); this.intentos=0; this.fallido=false; this.proximoIntento=null;
    }
}
