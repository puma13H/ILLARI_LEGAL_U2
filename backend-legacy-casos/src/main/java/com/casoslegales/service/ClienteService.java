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
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public ClienteService(ClienteRepository clienteRepository,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<ClienteResponse> listAll() {
        return clienteRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CreateUserResponse create(ClienteRequest request) {
        if (userRepository.existsByEmail(request.email()) || clienteRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("El email ya está registrado");
        }

        Role role = roleRepository.findByName("cliente")
                .orElseThrow(() -> new RuntimeException("Rol cliente no encontrado"));
        String password = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setName(request.nombre());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user = userRepository.save(user);

        Cliente cliente = new Cliente();
        cliente.setNombre(request.nombre());
        cliente.setTelefono(request.telefono());
        cliente.setEmail(request.email());
        cliente.setDireccion(request.direccion());
        cliente.setUser(user);
        clienteRepository.save(cliente);

        return new CreateUserResponse(request.email(), password);
    }

    public ClienteResponse update(Long id, ClienteRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        cliente.setNombre(request.nombre());
        cliente.setTelefono(request.telefono());
        cliente.setEmail(request.email());
        cliente.setDireccion(request.direccion());
        if (cliente.getUser() != null) {
            cliente.getUser().setName(request.nombre());
            cliente.getUser().setEmail(request.email());
        }
        return toResponse(clienteRepository.save(cliente));
    }

    @Transactional
    public void delete(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        if (cliente.getUser() != null) {
            userRepository.delete(cliente.getUser());
        }
        clienteRepository.delete(cliente);
    }

    private ClienteResponse toResponse(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNombre(), c.getTelefono(), c.getEmail(),
                c.getDireccion(), c.getUser() != null ? c.getUser().getId() : null);
    }
}
