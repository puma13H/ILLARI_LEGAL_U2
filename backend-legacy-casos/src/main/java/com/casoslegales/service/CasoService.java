package com.casoslegales.service;

import com.casoslegales.dto.CasoRequest;
import com.casoslegales.dto.CasoResponse;
import com.casoslegales.model.*;
import com.casoslegales.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CasoService {

    private final CasoRepository casoRepository;
    private final ClienteRepository clienteRepository;
    private final AbogadoRepository abogadoRepository;
    private final UserRepository userRepository;

    public CasoService(CasoRepository casoRepository,
                       ClienteRepository clienteRepository,
                       AbogadoRepository abogadoRepository,
                       UserRepository userRepository) {
        this.casoRepository = casoRepository;
        this.clienteRepository = clienteRepository;
        this.abogadoRepository = abogadoRepository;
        this.userRepository = userRepository;
    }

    public List<CasoResponse> listForUser(String email, String role) {
        return switch (role) {
            case "admin" -> casoRepository.findAll().stream().map(this::toResponse).toList();
            case "abogado" -> {
                Abogado abogado = abogadoRepository.findByUserId(getUserId(email))
                        .orElseThrow(() -> new RuntimeException("Abogado no encontrado"));
                yield casoRepository.findByAbogadoIdOrderByIdDesc(abogado.getId())
                        .stream().map(this::toResponse).toList();
            }
            case "cliente" -> {
                Cliente cliente = clienteRepository.findByUserId(getUserId(email))
                        .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
                yield casoRepository.findByClienteIdOrderByIdDesc(cliente.getId())
                        .stream().map(this::toResponse).toList();
            }
            default -> throw new RuntimeException("Rol no soportado");
        };
    }

    public CasoResponse create(CasoRequest request, String email, String role) {
        Abogado abogado;
        if ("admin".equals(role)) {
            if (request.abogadoId() == null) {
                throw new RuntimeException("Abogado es requerido");
            }
            abogado = abogadoRepository.findById(request.abogadoId())
                    .orElseThrow(() -> new RuntimeException("Abogado no encontrado"));
        } else {
            abogado = abogadoRepository.findByUserId(getUserId(email))
                    .orElseThrow(() -> new RuntimeException("Abogado no encontrado"));
            if (request.abogadoId() != null && !request.abogadoId().equals(abogado.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("No puedes asignar otro abogado");
            }
        }

        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        Caso caso = new Caso();
        caso.setNombreCaso(request.nombreCaso());
        caso.setCliente(cliente);
        caso.setAbogado(abogado);
        caso.setEstado(request.estado());
        caso.setFechaApertura(request.fechaApertura());
        caso.setDescripcion(request.descripcion());
        caso.setTarifa(request.tarifa());
        caso.setFechaAudiencia(request.fechaAudiencia());

        return toResponse(casoRepository.save(caso));
    }

    public CasoResponse getById(Long id, String email, String role) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Caso no encontrado"));
        validateAccess(caso, email, role);
        return toResponse(caso);
    }

    public CasoResponse update(Long id, CasoRequest request, String email, String role) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Caso no encontrado"));
        validateAccess(caso, email, role);

        if (!"admin".equals(role) && !"abogado".equals(role)) {
            throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para editar casos");
        }

        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        caso.setNombreCaso(request.nombreCaso());
        caso.setCliente(cliente);
        caso.setEstado(request.estado());
        caso.setFechaApertura(request.fechaApertura());
        caso.setDescripcion(request.descripcion());
        caso.setTarifa(request.tarifa());
        caso.setFechaAudiencia(request.fechaAudiencia());

        return toResponse(casoRepository.save(caso));
    }

    public void delete(Long id, String email, String role) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Caso no encontrado"));
        validateAccess(caso, email, role);
        if (!"abogado".equals(role) && !"admin".equals(role)) {
            throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para eliminar casos");
        }
        casoRepository.delete(caso);
    }

    private void validateAccess(Caso caso, String email, String role) {
        if ("admin".equals(role)) return;

        if ("abogado".equals(role)) {
            Abogado abogado = abogadoRepository.findByUserId(getUserId(email))
                    .orElseThrow(() -> new RuntimeException("Abogado no encontrado"));
            if (!caso.getAbogado().getId().equals(abogado.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado");
            }
            return;
        }

        if ("cliente".equals(role)) {
            Cliente cliente = clienteRepository.findByUserId(getUserId(email))
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
            if (!caso.getCliente().getId().equals(cliente.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado");
            }
            return;
        }

        throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado");
    }

    private Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private CasoResponse toResponse(Caso caso) {
        return new CasoResponse(
                caso.getId(),
                caso.getNombreCaso(),
                caso.getCliente().getId(),
                caso.getCliente().getNombre(),
                caso.getAbogado().getId(),
                caso.getAbogado().getNombre(),
                caso.getEstado(),
                caso.getFechaApertura(),
                caso.getDescripcion(),
                caso.getTarifa(),
                caso.getFechaAudiencia()
        );
    }
}
