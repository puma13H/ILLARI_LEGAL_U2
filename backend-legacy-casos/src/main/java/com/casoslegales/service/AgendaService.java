package com.casoslegales.service;

import com.casoslegales.dto.*;
import com.casoslegales.model.Agenda;
import com.casoslegales.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgendaService {

    private final AgendaRepository agendaRepository;
    private final CasoRepository casoRepository;
    private final ClienteRepository clienteRepository;
    private final AbogadoRepository abogadoRepository;

    public AgendaService(AgendaRepository agendaRepository,
                         CasoRepository casoRepository,
                         ClienteRepository clienteRepository,
                         AbogadoRepository abogadoRepository) {
        this.agendaRepository = agendaRepository;
        this.casoRepository = casoRepository;
        this.clienteRepository = clienteRepository;
        this.abogadoRepository = abogadoRepository;
    }

    public List<AgendaResponse> listAll() {
        return agendaRepository.findAll().stream().map(this::toResponse).toList();
    }

    public AgendaResponse create(AgendaRequest request) {
        Agenda agenda = new Agenda();
        agenda.setTitulo(request.titulo());
        agenda.setDescripcion(request.descripcion());
        agenda.setFechaInicio(request.fechaInicio());
        agenda.setFechaFin(request.fechaFin());
        return toResponse(agendaRepository.save(agenda));
    }

    public DashboardResponse dashboard() {
        long totalCasos = casoRepository.count();
        long totalClientes = clienteRepository.count();
        long totalAbogados = abogadoRepository.count();
        long casosActivos = casoRepository.findAll().stream()
                .filter(c -> !c.getEstado().equalsIgnoreCase("Cerrado")
                        && !c.getEstado().equalsIgnoreCase("Concluido"))
                .count();
        return new DashboardResponse(totalCasos, totalClientes, totalAbogados, casosActivos);
    }

    private AgendaResponse toResponse(Agenda a) {
        return new AgendaResponse(a.getId(), a.getTitulo(), a.getDescripcion(),
                a.getFechaInicio(), a.getFechaFin());
    }
}
