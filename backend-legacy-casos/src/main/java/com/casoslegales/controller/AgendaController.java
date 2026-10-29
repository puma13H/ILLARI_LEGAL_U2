package com.casoslegales.controller;

import com.casoslegales.dto.*;
import com.casoslegales.service.AgendaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agenda")
@PreAuthorize("hasRole('admin')")
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping
    public ResponseEntity<List<AgendaResponse>> list() {
        return ResponseEntity.ok(agendaService.listAll());
    }

    @PostMapping
    public ResponseEntity<AgendaResponse> create(@Valid @RequestBody AgendaRequest request) {
        return ResponseEntity.ok(agendaService.create(request));
    }
}
