package com.illarilegal.pago;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pagos")
public class PagoController {
    private final PagoService pagos;

    public PagoController(PagoService pagos) {
        this.pagos = pagos;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoService.Pago> obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        PagoService.Pago pago = pagos.obtener(id);
        if (pago == null || (!esAdministrador(jwt) && !pago.idCliente().equals(jwt.getSubject()))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pago);
    }

    @GetMapping("/orden/{idOrden}")
    public ResponseEntity<List<PagoService.Pago>> porOrden(@PathVariable Long idOrden, @AuthenticationPrincipal Jwt jwt) {
        List<PagoService.Pago> pagosOrden = pagos.porOrden(idOrden);
        if (!esAdministrador(jwt) && pagosOrden.stream().anyMatch(pago -> !pago.idCliente().equals(jwt.getSubject()))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pagosOrden);
    }

    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestBody(required = false) Map<String, Object> payload) {
        return Map.of(
                "recibido", true,
                "mensaje", "Webhook de demostracion. Configurar una pasarela de pago posteriormente.",
                "payload", payload == null ? Map.of() : payload);
    }

    private boolean esAdministrador(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        return roles instanceof List<?> lista
                && lista.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.toString()));
    }
}
