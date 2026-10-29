package com.illarilegal.cliente;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {
    private final ClienteRepository clientes;
    public ClienteController(ClienteRepository clientes) { this.clientes = clientes; }
    @GetMapping("/me")
    public ResponseEntity<Cliente> miPerfil(@AuthenticationPrincipal Jwt jwt) {
        return clientes.findByKeycloakSub(jwt.getSubject()).map(this::aCliente)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping("/me")
    @Transactional
    public ResponseEntity<Cliente> crearMiPerfil(@AuthenticationPrincipal Jwt jwt, @RequestBody Cliente solicitud) {
        if (clientes.existsByKeycloakSub(jwt.getSubject()))
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        ClienteEntity nuevo = clientes.save(nuevaEntidad(solicitud, jwt.getSubject()));
        return ResponseEntity.status(HttpStatus.CREATED).body(aCliente(nuevo));
    }
    @GetMapping
    public List<Cliente> listar(){return clientes.findAll().stream().map(this::aCliente).toList();}
    @GetMapping("/{id}")
    public ResponseEntity<Cliente> obtener(@PathVariable Long id) {
        return clientes.findById(id).map(this::aCliente).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping
    @Transactional
    public Cliente crear(@RequestBody Cliente solicitud){
        return aCliente(clientes.save(nuevaEntidad(solicitud, solicitud.keycloakSub())));
    }
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Cliente> actualizar(@PathVariable Long id, @RequestBody Cliente solicitud) {
        return clientes.findById(id).map(e -> {
            e.modificar(solicitud.dni(),solicitud.nombres(),solicitud.apellidos(),solicitud.email(),
                    solicitud.telefono(),solicitud.direccion(),solicitud.estado());
            return ResponseEntity.ok(aCliente(clientes.save(e)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
    private ClienteEntity nuevaEntidad(Cliente c,String sub){
        if(c == null || c.nombres()==null || c.nombres().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requieren nombres del cliente");
        return new ClienteEntity(c.dni(),c.nombres(),c.apellidos(),c.email(),c.telefono(),c.direccion(),
                c.estado()==null?"ACTIVO":c.estado(),sub);
    }
    private Cliente aCliente(ClienteEntity e){return new Cliente(e.getId(),e.getDni(),e.getNombres(),e.getApellidos(),
            e.getEmail(),e.getTelefono(),e.getDireccion(),e.getEstado(),e.getKeycloakSub());}
    public record Cliente(Long id,String dni,String nombres,String apellidos,String email,String telefono,
            String direccion,String estado,String keycloakSub) {}
}
