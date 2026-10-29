package com.illarilegal.ordenlegal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ordenes_legales", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_orden_pago_id", columnNames = "pago_id"))
public class OrdenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String idCliente;

    @Column(nullable = false)
    private Instant fechaCreacion;

    @Column(nullable = false)
    private String estado;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private String metodoPago;

    @Column(nullable = false, columnDefinition = "text")
    private String detallesJson;
    @jakarta.persistence.OneToMany(mappedBy = "orden", cascade = jakarta.persistence.CascadeType.ALL,
            fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.OrderBy("posicion ASC")
    private java.util.List<OrdenDetalleEntity> detalles = new java.util.ArrayList<>();

    // Nullable for orders created before the validated payment-result contract.
    @Column(name = "pago_id")
    private Long pagoId;
    private Long revisionPago;
    private String estadoPago;

    protected OrdenEntity() {}

    public OrdenEntity(String idCliente, Instant fechaCreacion, String estado, BigDecimal total, String metodoPago, String detallesJson) {
        this.idCliente = idCliente;
        this.fechaCreacion = fechaCreacion;
        this.estado = estado;
        this.total = total;
        this.metodoPago = metodoPago;
        this.detallesJson = detallesJson;
    }

    public Long getId() { return id; }
    public String getIdCliente() { return idCliente; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public String getEstado() { return estado; }
    public BigDecimal getTotal() { return total; }
    public String getMetodoPago() { return metodoPago; }
    public String getDetallesJson() { return detallesJson; }
    public java.util.List<OrdenDetalleEntity> getDetalles() { return detalles; }
    public void agregarDetalle(OrdenLegalController.DetalleOrden d) {
        detalles.add(new OrdenDetalleEntity(this, detalles.size(), d));
    }
    public void setEstado(String estado) { this.estado = estado; }
    public Long getPagoId() { return pagoId; }
    public Long getRevisionPago() { return revisionPago; }
    public String getEstadoPago() { return estadoPago; }
    public void registrarPago(Long pagoId, Long revision, String estadoPago) {
        this.pagoId = pagoId;
        this.revisionPago = revision;
        this.estadoPago = estadoPago;
    }
}
