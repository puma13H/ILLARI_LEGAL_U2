package com.casoslegales.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="keycloak_identidades",uniqueConstraints={
    @UniqueConstraint(name="uk_identidad_issuer_sub",columnNames={"issuer","subject"}),
    @UniqueConstraint(name="uk_identidad_usuario_issuer",columnNames={"user_id","issuer"})})
public class IdentidadKeycloak {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String issuer;
    @Column(nullable=false) private String subject;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(nullable=false) private Instant creada;
    @Column(nullable=false) private String operador;
    protected IdentidadKeycloak(){}
    public IdentidadKeycloak(String issuer,String subject,User user,String operador){
        this.issuer=issuer;this.subject=subject;this.user=user;this.operador=operador;this.creada=Instant.now();
    }
    public User getUser(){return user;}
    public String getSubject(){return subject;}
}

