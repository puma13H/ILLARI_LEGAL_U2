package com.casoslegales.controller;

import com.casoslegales.dto.*;
import com.casoslegales.service.AbogadoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/abogados")
@PreAuthorize("hasRole('admin')")
public class AbogadoController {

    private final AbogadoService abogadoService;

    public AbogadoController(AbogadoService abogadoService) {
        this.abogadoService = abogadoService;
    }

    @GetMapping
    public ResponseEntity<List<AbogadoResponse>> list() {
        return ResponseEntity.ok(abogadoService.listAll());
    }

    @PostMapping
    public ResponseEntity<CreateUserResponse> create(@Valid @RequestBody AbogadoRequest request) {
        return ResponseEntity.ok(abogadoService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AbogadoResponse> update(@PathVariable Long id, @Valid @RequestBody AbogadoRequest request) {
        return ResponseEntity.ok(abogadoService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        abogadoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
