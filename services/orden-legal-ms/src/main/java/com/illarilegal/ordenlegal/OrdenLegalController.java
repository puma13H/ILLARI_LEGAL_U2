package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ordenes")
public class OrdenLegalController {
    private static final Logger log = LoggerFactory.getLogger(OrdenLegalController.class);
    private final ObjectMapper objectMapper;
    private final OrdenRepository ordenes;
    private final CatalogoResilienteService catalogoResiliente;
    private final OrdenOutboxRepository outbox;

    public OrdenLegalController(ObjectMapper objectMapper, OrdenRepository ordenes,
            CatalogoResilienteService catalogoResiliente, OrdenOutboxRepository outbox) {
        this.objectMapper = objectMapper;
        this.ordenes = ordenes;
        this.catalogoResiliente = catalogoResiliente;
        this.outbox = outbox;
    }

    private org.springframework.beans.factory.ObjectProvider<CompraIdempotenteService> compras;
    @Autowired
    public OrdenLegalController(ObjectMapper mapper, OrdenRepository repo, CatalogoResilienteService catalogo,
            OrdenOutboxRepository outbox, org.springframework.beans.factory.ObjectProvider<CompraIdempotenteService> compras) {
        this(mapper,repo,catalogo,outbox); this.compras=compras;
    }

    @PostMapping
    public OrdenLegal comprar(@RequestBody NuevaOrden request, @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String clave) {
        return compras.getObject().crear(clave,request,jwt);
    }

    @Transactional
    public OrdenLegal crear(@RequestBody NuevaOrden request, @AuthenticationPrincipal Jwt jwt) {
        request = ReglasCompra.normalizar(request,jwt);
        if (request == null || request.detalles() == null || request.detalles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La orden debe incluir al menos un servicio.");
        }
        if (request.metodoPago() == null || request.metodoPago().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un método de pago.");
        }

        List<DetalleOrden> detalles = request.detalles().stream()
                .map(this::crearDetalle)
                .toList();
        BigDecimal total = detalles.stream()
                .map(DetalleOrden::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una orden que requiere pago debe tener un total positivo.");
        }
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El total excede el importe admitido.");
        }

        final String detallesJson;
        try {
            detallesJson = objectMapper.writeValueAsString(detalles);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudieron guardar los detalles de la orden.", exception);
        }
        OrdenEntity nueva = new OrdenEntity(
                request.idCliente(), Instant.now(), "PENDIENTE_PAGO", total, request.metodoPago(), detallesJson);
        detalles.forEach(nueva::agregarDetalle);
        OrdenEntity entity = ordenes.save(nueva);
        OrdenLegal order = aOrden(entity);

        try {
            String event = objectMapper.writeValueAsString(new OrdenCreadaEvento(
                    "orden.creada", order.id(), order.idCliente(), total, order.metodoPago(), "orden-legal-ms", Instant.now()));
            // One local transaction commits the order and its durable event together.
            outbox.save(new OrdenOutboxEntity(order.id(), event));
            return order;
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar el evento de orden.", exception);
        }
    }

    private DetalleOrden crearDetalle(DetalleNuevaOrden item) {
        if (item == null || item.idServicio() == null || item.cantidad() == null || item.cantidad() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada servicio debe tener un identificador y una cantidad positiva.");
        }

        ServicioLegal servicio = catalogoResiliente.obtener(item.idServicio());

        if (servicio == null || !item.idServicio().equals(servicio.id()) || servicio.precio() == null
                || !("ACTIVO".equalsIgnoreCase(servicio.estado()) || "ACTIVA".equalsIgnoreCase(servicio.estado()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El servicio " + item.idServicio() + " no está disponible.");
        }
        if (servicio.precio().signum() < 0 || servicio.precio().stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio debe ser no negativo y tener como máximo dos decimales.");
        }
        BigDecimal subtotal = servicio.precio().multiply(BigDecimal.valueOf(item.cantidad()));
        return new DetalleOrden(servicio.id(), servicio.nombre(), item.cantidad(), servicio.precio(), subtotal);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrdenLegal> obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ordenes.findById(id)
                .filter(order -> esAdministrador(jwt) || order.getIdCliente().equals(jwt.getSubject()))
                .map(this::aOrden)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{idCliente}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrdenLegal>> porCliente(
            @PathVariable String idCliente,
            @AuthenticationPrincipal Jwt jwt) {
        if (!esAdministrador(jwt) && !idCliente.equals(jwt.getSubject())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ordenes.findByIdClienteOrderByFechaCreacionDesc(idCliente).stream()
                .map(this::aOrden)
                .toList());
    }

    @PatchMapping("/{id}/estado")
    @Transactional
    public ResponseEntity<OrdenLegal> estado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<OrdenEntity> current = ordenes.findForUpdate(id);
        if (current.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        OrdenEntity order = current.get();
        String destino = body == null ? null : body.get("estado");
        if (!"CANCELADA".equalsIgnoreCase(destino)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pago solo se confirma por eventos validados de Pagos.");
        }
        if ("CANCELADA".equals(order.getEstado()) || "CANCELACION_PENDIENTE".equals(order.getEstado())) {
            return ResponseEntity.ok(aOrden(order));
        }
        if (!List.of("PENDIENTE_PAGO", "PAGADA").contains(order.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden no admite cancelación en su estado actual.");
        }
        solicitarCompensacion(order);
        return ResponseEntity.ok(aOrden(ordenes.save(order)));
    }

    @Transactional
    public void registrarResultadoPago(PagoEventConsumer.PagoResultadoEvento event) {
        event.validarContrato();
        OrdenEntity order = ordenes.findForUpdate(event.ordenId())
                .orElseThrow(() -> new IllegalArgumentException("El resultado referencia una orden inexistente."));
        if (!order.getIdCliente().equals(event.idCliente()) || order.getTotal().compareTo(event.monto()) != 0) {
            throw new IllegalArgumentException("El cliente o importe del pago no coincide con la orden.");
        }
        if ((order.getPagoId() != null && !order.getPagoId().equals(event.pagoId()))
                || ordenes.existsByPagoIdAndIdNot(event.pagoId(), event.ordenId())) {
            throw new IllegalArgumentException("El identificador del pago ya corresponde a otra operación.");
        }
        if (order.getRevisionPago() != null) {
            if (event.revisionPago() < order.getRevisionPago()) {
                // A confirmed refund/cancellation supersedes its original approval, never the reverse.
                log.info("saga_resultado_anterior ordenId={} revision={}", event.ordenId(), event.revisionPago());
                return;
            }
            if (event.revisionPago().equals(order.getRevisionPago())) {
                if (!event.estado().equals(order.getEstadoPago())) {
                    throw new IllegalArgumentException("Resultados contradictorios para la misma revisión de pago.");
                }
                return;
            }
        }
        boolean compensado = event.revisionPago() == 2L;
        if (compensado) {
            if (!List.of("CANCELACION_PENDIENTE", "CANCELADA").contains(order.getEstado())) {
                throw new IllegalArgumentException("La orden no tiene una cancelación solicitada.");
            }
            order.setEstado("CANCELADA");
        } else if ("CANCELACION_PENDIENTE".equals(order.getEstado())) {
            // Keep waiting for the durable cancellation command's acknowledgement, even after approval.
        } else if ("CANCELADA".equals(order.getEstado()) && "VALIDADO".equals(event.estado())) {
            // Repair a pre-existing cancellation made before coordinated compensation existed.
            solicitarCompensacion(order);
        } else if ("PENDIENTE_PAGO".equals(order.getEstado())) {
            order.setEstado("VALIDADO".equals(event.estado()) ? "PAGADA" : "CANCELADA");
        } else if (!("PAGADA".equals(order.getEstado()) && "VALIDADO".equals(event.estado()))
                && !("CANCELADA".equals(order.getEstado()) && "FALLIDO".equals(event.estado()))) {
            throw new IllegalArgumentException("Transición incompatible con el estado de la orden.");
        }
        order.registrarPago(event.pagoId(), event.revisionPago(), event.estado());
        ordenes.save(order);
        log.info("saga_orden_actualizada ordenId={} estado={} revisionPago={}",
                event.ordenId(), order.getEstado(), event.revisionPago());
    }

    private void solicitarCompensacion(OrdenEntity order) {
        try {
            String payload = objectMapper.writeValueAsString(new OrdenCreadaEvento(
                    "orden.cancelacion.solicitada", order.getId(), order.getIdCliente(), order.getTotal(),
                    order.getMetodoPago(), "orden-legal-ms", Instant.now()));
            outbox.save(new OrdenOutboxEntity(order.getId(), payload));
            order.setEstado("CANCELACION_PENDIENTE");
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo registrar la compensación de la orden.", error);
        }
    }

    private OrdenLegal aOrden(OrdenEntity entity) {
        try {
            List<DetalleOrden> detalles = !entity.getDetalles().isEmpty()
                    ? entity.getDetalles().stream().map(OrdenDetalleEntity::snapshot).toList() : objectMapper.readValue(
                    entity.getDetallesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DetalleOrden.class));
            return new OrdenLegal(
                    entity.getId(),
                    entity.getIdCliente(),
                    entity.getFechaCreacion(),
                    entity.getEstado(),
                    entity.getTotal(),
                    entity.getMetodoPago(),
                    detalles);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudieron leer los detalles almacenados de la orden " + entity.getId(), exception);
        }
    }

    private boolean esAdministrador(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> claims && claims.get("roles") instanceof Collection<?> roles) {
            return roles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.toString()));
        }
        return false;
    }

    public record NuevaOrden(String metodoPago, List<DetalleNuevaOrden> detalles, String idCliente) {
        public NuevaOrden(String metodoPago, List<DetalleNuevaOrden> detalles) { this(metodoPago,detalles,null); }
    }
    public record DetalleNuevaOrden(Long idServicio, Integer cantidad) {}
    public record DetalleOrden(Long idServicio, String nombreServicio, Integer cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {}
    public record OrdenLegal(
            Long id,
            String idCliente,
            Instant fechaCreacion,
            String estado,
            BigDecimal total,
            String metodoPago,
            List<DetalleOrden> detalles) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record OrdenCreadaEvento(
            String tipoEvento,
            Long ordenId,
            String idCliente,
            BigDecimal total,
            String metodoPago,
            String origen,
            Instant timestamp) {}
    public record ServicioLegal(Long id, String nombre, String descripcion, BigDecimal precio, String categoria, String estado) {}
}
