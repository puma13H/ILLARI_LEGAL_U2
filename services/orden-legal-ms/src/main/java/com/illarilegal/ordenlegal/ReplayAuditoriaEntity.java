package com.illarilegal.ordenlegal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="orden_replay_auditoria")
public class ReplayAuditoriaEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String tipo;
    private String referencia;
    private String operador;
    @Column(length=500) private String motivo;
    @Column(columnDefinition="text") private String payload;
    private Instant fecha;
    protected ReplayAuditoriaEntity(){}
    public ReplayAuditoriaEntity(String tipo,String referencia,String operador,String motivo,String payload) {
        this.tipo=tipo;this.referencia=referencia;this.operador=operador;this.motivo=motivo;this.payload=payload;fecha=Instant.now();
    }
    public record Vista(Long id,String tipo,String referencia,String operador,String motivo,Instant fecha){}
    public Vista vista(){return new Vista(id,tipo,referencia,operador,motivo,fecha);}
}

