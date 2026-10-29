package com.illarilegal.ordenlegal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "compra_idempotencia")
public class CompraIdempotenciaEntity implements org.springframework.data.domain.Persistable<String> {
    @Transient private boolean nueva=true;
    @Override public String getId(){return id;}
    @Override @Transient public boolean isNew(){return nueva;}
    @PostLoad @PostPersist void persistida(){nueva=false;}
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private String cliente;
    @Column(nullable = false, length = 128) private String clave;
    @Column(nullable = false, length = 64) private String huella;
    @Column(columnDefinition = "text") private String respuesta;
    private Long ordenId;
    @Column(nullable = false) private Instant creada;
    protected CompraIdempotenciaEntity() {}
    public CompraIdempotenciaEntity(String id, String cliente, String clave, String huella) {
        this.id=id; this.cliente=cliente; this.clave=clave; this.huella=huella; creada=Instant.now();
    }
    public String getHuella() { return huella; }
    public String getRespuesta() { return respuesta; }
    public void completar(Long ordenId, String respuesta) { this.ordenId=ordenId; this.respuesta=respuesta; }
}

