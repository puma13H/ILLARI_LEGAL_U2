package com.casoslegales.service;

import com.casoslegales.dto.*;
import com.casoslegales.model.*;
import com.casoslegales.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AbogadoService {

    private final AbogadoRepository abogadoRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AbogadoService(AbogadoRepository abogadoRepository,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.abogadoRepository = abogadoRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AbogadoResponse> listAll() {
        return abogadoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CreateUserResponse create(AbogadoRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("El email ya está registrado");
        }

        Role role = roleRepository.findByName("abogado")
                .orElseThrow(() -> new RuntimeException("Rol abogado no encontrado"));
        String password = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setName(request.nombre());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user = userRepository.save(user);

        Abogado abogado = new Abogado();
        abogado.setNombre(request.nombre());
        abogado.setTelefono(request.telefono());
        abogado.setEmail(request.email());
        abogado.setEspecialidad(request.especialidad());
        abogado.setDireccion(request.direccion());
        abogado.setUser(user);
        abogadoRepository.save(abogado);

        return new CreateUserResponse(request.email(), password);
    }

    public AbogadoResponse update(Long id, AbogadoRequest request) {
        Abogado abogado = abogadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Abogado no encontrado"));
        abogado.setNombre(request.nombre());
        abogado.setTelefono(request.telefono());
        abogado.setEmail(request.email());
        abogado.setEspecialidad(request.especialidad());
        abogado.setDireccion(request.direccion());
        if (abogado.getUser() != null) {
            abogado.getUser().setName(request.nombre());
            abogado.getUser().setEmail(request.email());
        }
        return toResponse(abogadoRepository.save(abogado));
    }

    @Transactional
    public void delete(Long id) {
        Abogado abogado = abogadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Abogado no encontrado"));
        if (abogado.getUser() != null) {
            userRepository.delete(abogado.getUser());
        }
        abogadoRepository.delete(abogado);
    }

    private AbogadoResponse toResponse(Abogado a) {
        return new AbogadoResponse(a.getId(), a.getNombre(), a.getTelefono(), a.getEmail(),
                a.getEspecialidad(), a.getDireccion(), a.getUser() != null ? a.getUser().getId() : null);
    }
}
