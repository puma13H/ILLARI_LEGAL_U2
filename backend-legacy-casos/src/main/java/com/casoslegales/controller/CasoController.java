package com.casoslegales.controller;

import com.casoslegales.dto.CasoRequest;
import com.casoslegales.dto.CasoResponse;
import com.casoslegales.service.CasoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/casos")
public class CasoController {

    private final CasoService casoService;

    public CasoController(CasoService casoService) {
        this.casoService = casoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('admin', 'abogado', 'cliente')")
    public ResponseEntity<List<CasoResponse>> list(Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(casoService.listForUser(auth.getName(), role));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin', 'abogado', 'cliente')")
    public ResponseEntity<CasoResponse> get(@PathVariable Long id, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(casoService.getById(id, auth.getName(), role));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('admin','abogado')")
    public ResponseEntity<CasoResponse> create(@Valid @RequestBody CasoRequest request, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(casoService.create(request, auth.getName(), role));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin','abogado')")
    public ResponseEntity<CasoResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody CasoRequest request,
                                               Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(casoService.update(id, request, auth.getName(), role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin','abogado')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        casoService.delete(id, auth.getName(), role);
        return ResponseEntity.noContent().build();
    }
}
