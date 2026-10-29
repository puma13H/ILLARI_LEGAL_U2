package com.casoslegales.model;

import jakarta.persistence.*;

@Entity
@Table(name = "mensajes")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id", nullable = false)
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Caso getCaso() { return caso; }
    public void setCaso(Caso caso) { this.caso = caso; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
}
