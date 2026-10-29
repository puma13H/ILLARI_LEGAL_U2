package com.illarilegal.pago;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="pago_dead_letter")
public class MensajeFallidoEntity implements org.springframework.data.domain.Persistable<String> {
    @Transient boolean nuevo=true;
    @Override public String getId(){return id;}
    @Override @Transient public boolean isNew(){return nuevo;}
    @PostLoad @PostPersist void persistido(){nuevo=false;}
    @Id @Column(length=36) String id;
    @Column(nullable=false) String topic;
    int particion;
    long offsetOriginal;
    @Column(nullable=false,columnDefinition="text") String payload;
    String errorTipo;
    @Column(nullable=false) String estado="DLT";
    Instant creado=Instant.now();
    @Column(columnDefinition="text") String payloadReplay;
    String operador;
    @Column(length=500) String motivo;
    Instant solicitado;
    Instant proximoIntento;
    int intentos;
    int reprocesos;
    protected MensajeFallidoEntity(){}
    MensajeFallidoEntity(String id,String topic,int partition,long offset,String payload,String error) {
        this.id=id;this.topic=topic;this.particion=partition;this.offsetOriginal=offset;this.payload=payload;this.errorTipo=error;
    }
}

