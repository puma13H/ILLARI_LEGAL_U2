package com.casoslegales.service;

import com.casoslegales.dto.*;
import com.casoslegales.model.*;
import com.casoslegales.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MensajeService {

    private final MensajeRepository mensajeRepository;
    private final CasoRepository casoRepository;
    private final UserRepository userRepository;
    private final CasoService casoService;

    public MensajeService(MensajeRepository mensajeRepository,
                          CasoRepository casoRepository,
                          UserRepository userRepository,
                          @Lazy CasoService casoService) {
        this.mensajeRepository = mensajeRepository;
        this.casoRepository = casoRepository;
        this.userRepository = userRepository;
        this.casoService = casoService;
    }

    public List<MensajeResponse> listByCaso(Long casoId, String email, String role) {
        casoService.getById(casoId, email, role);
        return mensajeRepository.findByCasoIdOrderByIdAsc(casoId).stream()
                .map(this::toResponse).toList();
    }

    public MensajeResponse create(Long casoId, MensajeRequest request, String email, String role) {
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new RuntimeException("Caso no encontrado"));
        casoService.getById(casoId, email, role);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Mensaje mensaje = new Mensaje();
        mensaje.setCaso(caso);
        mensaje.setUser(user);
        mensaje.setContenido(request.contenido());

        return toResponse(mensajeRepository.save(mensaje));
    }

    private MensajeResponse toResponse(Mensaje m) {
        return new MensajeResponse(m.getId(), m.getCaso().getId(), m.getUser().getId(),
                m.getUser().getName(), m.getContenido());
    }
}
