package com.illarilegal.pago;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="pago_inbox")
public class EventoRecibidoEntity implements org.springframework.data.domain.Persistable<String> {
    @Transient private boolean nuevo=true;
    @Override public String getId(){return eventId;}
    @Override @Transient public boolean isNew(){return nuevo;}
    @PostLoad @PostPersist void persistido(){nuevo=false;}
    @Id @Column(length=36) private String eventId;
    @Column(nullable=false,length=64) private String huella;
    @Column(nullable=false) private Instant procesado;
    protected EventoRecibidoEntity(){}
    public EventoRecibidoEntity(String id,String hash){eventId=id;huella=hash;procesado=Instant.now();}
    public void verificar(String hash) {
        if(!huella.equals(hash)) throw new IllegalArgumentException("eventId reutilizado con contenido distinto.");
    }
}

