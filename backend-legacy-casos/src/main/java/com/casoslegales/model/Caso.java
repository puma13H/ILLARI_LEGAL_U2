package com.casoslegales.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "casos")
public class Caso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombreCaso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abogado_id", nullable = false)
    private Abogado abogado;

    @Column(nullable = false)
    private String estado = "Iniciado";

    @Column(nullable = false)
    private LocalDate fechaApertura;

    private String descripcion;

    private BigDecimal tarifa = BigDecimal.ZERO;

    private LocalDate fechaAudiencia;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombreCaso() { return nombreCaso; }
    public void setNombreCaso(String nombreCaso) { this.nombreCaso = nombreCaso; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public Abogado getAbogado() { return abogado; }
    public void setAbogado(Abogado abogado) { this.abogado = abogado; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDate getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDate fechaApertura) { this.fechaApertura = fechaApertura; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getTarifa() { return tarifa; }
    public void setTarifa(BigDecimal tarifa) { this.tarifa = tarifa; }
    public LocalDate getFechaAudiencia() { return fechaAudiencia; }
    public void setFechaAudiencia(LocalDate fechaAudiencia) { this.fechaAudiencia = fechaAudiencia; }
}
