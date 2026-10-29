package com.casoslegales.controller;

import com.casoslegales.dto.*;
import com.casoslegales.service.MensajeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/casos/{casoId}/mensajes")
public class MensajeController {

    private final MensajeService mensajeService;

    public MensajeController(MensajeService mensajeService) {
        this.mensajeService = mensajeService;
    }

    @GetMapping
    public ResponseEntity<List<MensajeResponse>> list(@PathVariable Long casoId, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(mensajeService.listByCaso(casoId, auth.getName(), role));
    }

    @PostMapping
    public ResponseEntity<MensajeResponse> create(@PathVariable Long casoId,
                                                   @Valid @RequestBody MensajeRequest request,
                                                   Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(mensajeService.create(casoId, request, auth.getName(), role));
    }
}
