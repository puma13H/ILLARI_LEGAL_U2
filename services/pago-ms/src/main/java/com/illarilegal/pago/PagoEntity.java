package com.illarilegal.pago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pagos_legales")
public class PagoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long idOrden;

    @Column(nullable = false)
    private String idCliente;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private Instant fechaCreacion;

    private Instant fechaAprobacion;

    protected PagoEntity() {}

    public PagoEntity(Long idOrden, String idCliente, BigDecimal monto, String estado, Instant fechaCreacion, Instant fechaAprobacion) {
        this.idOrden = idOrden;
        this.idCliente = idCliente;
        this.monto = monto;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.fechaAprobacion = fechaAprobacion;
    }

    public Long getId() { return id; }
    public Long getIdOrden() { return idOrden; }
    public String getIdCliente() { return idCliente; }
    public BigDecimal getMonto() { return monto; }
    public String getEstado() { return estado; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaAprobacion() { return fechaAprobacion; }
    public void setEstado(String estado) { this.estado = estado; }
    public void setFechaAprobacion(Instant fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }
}
