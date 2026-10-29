package com.casoslegales.controller;

import com.casoslegales.dto.*;
import com.casoslegales.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    @PreAuthorize("hasRole('admin') or hasRole('abogado')")
    public ResponseEntity<List<ClienteResponse>> list() {
        return ResponseEntity.ok(clienteService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('admin') or hasRole('abogado')")
    public ResponseEntity<CreateUserResponse> create(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ClienteResponse> update(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        clienteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
