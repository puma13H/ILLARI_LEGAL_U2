package com.casoslegales.controller;

import com.casoslegales.dto.DashboardResponse;
import com.casoslegales.service.AgendaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AgendaService agendaService;
    private final com.casoslegales.service.CasoService casos;

    public DashboardController(AgendaService agendaService, com.casoslegales.service.CasoService casos) {
        this.agendaService = agendaService;
        this.casos = casos;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('admin','abogado','cliente')")
    public ResponseEntity<DashboardResponse> stats(org.springframework.security.core.Authentication auth) {
        String role=auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_","");
        if("admin".equals(role))return ResponseEntity.ok(agendaService.dashboard());
        var visibles=casos.listForUser(auth.getName(),role);
        long activos=visibles.stream().filter(c->!"Cerrado".equalsIgnoreCase(c.estado()) && !"Concluido".equalsIgnoreCase(c.estado())).count();
        return ResponseEntity.ok(new DashboardResponse(visibles.size(),visibles.stream().map(c->c.clienteId()).distinct().count(),
                visibles.stream().map(c->c.abogadoId()).distinct().count(),activos));
    }
}
