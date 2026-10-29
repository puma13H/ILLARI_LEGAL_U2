package com.illarilegal.serviciolegal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/servicios")
public class ServicioLegalController {
    private final ServicioLegalRepository servicios;

    public ServicioLegalController(ServicioLegalRepository servicios) {
        this.servicios = servicios;
    }

    @GetMapping
    public List<ServicioLegal> listar() {
        return servicios.findAll().stream()
                .sorted(Comparator.comparing(ServicioLegalEntity::getId))
                .map(this::aServicio)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServicioLegal> obtener(@PathVariable Long id) {
        return servicios.findById(id).map(this::aServicio)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ServicioLegal crear(@RequestBody ServicioLegal request) {
        validar(request);
        String estado = normalizarEstado(request.estado() == null ? "ACTIVO" : request.estado());
        return aServicio(servicios.save(new ServicioLegalEntity(
                request.nombre().trim(),
                request.descripcion(),
                request.precio(),
                request.categoria().trim(),
                estado)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicioLegal> actualizar(@PathVariable Long id, @RequestBody ServicioLegal request) {
        var existente = servicios.findById(id);
        if (existente.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        validar(request);
        ServicioLegalEntity servicio = existente.get();
        servicio.setNombre(request.nombre().trim());
        servicio.setDescripcion(request.descripcion());
        servicio.setPrecio(request.precio());
        servicio.setCategoria(request.categoria().trim());
        if (request.estado() != null && !request.estado().isBlank()) {
            servicio.setEstado(normalizarEstado(request.estado()));
        }
        return ResponseEntity.ok(aServicio(servicios.save(servicio)));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<ServicioLegal> estado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        var existente = servicios.findById(id);
        if (existente.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ServicioLegalEntity servicio = existente.get();
        if (body == null || !body.containsKey("estado")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere estado.");
        servicio.setEstado(normalizarEstado(body.get("estado")));
        return ResponseEntity.ok(aServicio(servicios.save(servicio)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!servicios.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        servicios.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void validar(ServicioLegal servicio) {
        if (servicio == null || servicio.nombre() == null || servicio.nombre().isBlank()
                || servicio.precio() == null || servicio.precio().signum() < 0
                || servicio.precio().stripTrailingZeros().scale() > 2 || servicio.precio().compareTo(new BigDecimal("9999999999.99")) > 0
                || servicio.categoria() == null || servicio.categoria().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Nombre, categoria y un precio no negativo son obligatorios.");
        }
    }

    private String normalizarEstado(String estado) {
        String e = estado == null ? "" : estado.trim().toUpperCase(java.util.Locale.ROOT);
        if ("ACTIVA".equals(e)) e="ACTIVO"; if ("INACTIVA".equals(e)) e="INACTIVO";
        if (!java.util.Set.of("ACTIVO","INACTIVO").contains(e)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado no admitido.");
        return e;
    }
    private ServicioLegal aServicio(ServicioLegalEntity entity) {
        return new ServicioLegal(
                entity.getId(),
                entity.getNombre(),
                entity.getDescripcion(),
                entity.getPrecio(),
                entity.getCategoria(),
                entity.getEstado());
    }

    public record ServicioLegal(Long id, String nombre, String descripcion, BigDecimal precio, String categoria, String estado) {}
}
