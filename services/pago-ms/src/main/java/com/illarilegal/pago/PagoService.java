package com.illarilegal.pago;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class PagoService {
    private final PagoRepository pagos;

    public PagoService(PagoRepository pagos) {
        this.pagos = pagos;
    }

    @Transactional
    public Pago iniciarDesdeOrden(Long idOrden, String idCliente, BigDecimal monto) {
        validarDatos(idOrden, idCliente, monto);
        return pagos.findByOrdenForUpdate(idOrden)
                .map(entity -> { validarCorrespondencia(entity, idCliente, monto); return aPago(entity); })
                .orElseGet(() -> aPago(pagos.saveAndFlush(new PagoEntity(
                        idOrden, idCliente, monto, "PENDIENTE", Instant.now(), null))));
    }

    @Transactional
    public Pago resolver(Long idPago, boolean aprobado) {
        return pagos.findForUpdate(idPago).map(entity -> {
            if ("PENDIENTE".equals(entity.getEstado())) {
                entity.setEstado(aprobado ? "VALIDADO" : "FALLIDO");
                entity.setFechaAprobacion(aprobado ? Instant.now() : null);
                pagos.save(entity);
            }
            return aPago(entity);
        }).orElseThrow(() -> new IllegalArgumentException("Pago inexistente."));
    }

    @Transactional
    public Pago cancelarDesdeOrden(Long idOrden, String idCliente, BigDecimal monto) {
        validarDatos(idOrden, idCliente, monto);
        PagoEntity entity = pagos.findByOrdenForUpdate(idOrden).orElseGet(() ->
                pagos.saveAndFlush(new PagoEntity(idOrden, idCliente, monto, "CANCELADO", Instant.now(), null)));
        validarCorrespondencia(entity, idCliente, monto);
        // A cancellation received before creation is a durable tombstone: replay can never charge it.
        switch (entity.getEstado()) {
            case "VALIDADO" -> entity.setEstado("REEMBOLSADO");
            case "PENDIENTE", "FALLIDO" -> entity.setEstado("CANCELADO");
            case "CANCELADO", "REEMBOLSADO" -> { }
            default -> throw new IllegalStateException("Estado de pago desconocido.");
        }
        return aPago(pagos.save(entity));
    }

    private void validarDatos(Long idOrden, String cliente, BigDecimal monto) {
        if (idOrden == null || idOrden < 1 || cliente == null || cliente.isBlank()
                || monto == null || monto.signum() <= 0 || monto.stripTrailingZeros().scale() > 2
                || monto.compareTo(new BigDecimal("9999999999.99"))>0) {
            throw new IllegalArgumentException("El pago requiere orden, cliente e importe positivo con dos decimales como máximo.");
        }
    }

    private void validarCorrespondencia(PagoEntity entity, String cliente, BigDecimal monto) {
        if (!entity.getIdCliente().equals(cliente) || entity.getMonto().compareTo(monto) != 0) {
            throw new IllegalArgumentException("El evento contradice el cliente o importe del pago existente.");
        }
    }

    @Transactional(readOnly = true)
    public Pago obtener(Long id) {
        return pagos.findById(id).map(this::aPago).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Pago> porOrden(Long idOrden) {
        return pagos.findAllByIdOrdenOrderByFechaCreacionAsc(idOrden).stream().map(this::aPago).toList();
    }

    private Pago aPago(PagoEntity entity) {
        return new Pago(
                entity.getId(),
                entity.getIdOrden(),
                entity.getIdCliente(),
                entity.getMonto(),
                entity.getEstado(),
                entity.getFechaCreacion(),
                entity.getFechaAprobacion());
    }

    public record Pago(
            Long id,
            Long idOrden,
            String idCliente,
            BigDecimal monto,
            String estado,
            Instant fechaCreacion,
            Instant fechaAprobacion) {
        public long revision() {
            return switch (estado) {
                case "VALIDADO", "FALLIDO" -> 1L;
                case "CANCELADO", "REEMBOLSADO" -> 2L;
                default -> throw new IllegalStateException("El pago todavía no tiene resultado.");
            };
        }
    }
}
