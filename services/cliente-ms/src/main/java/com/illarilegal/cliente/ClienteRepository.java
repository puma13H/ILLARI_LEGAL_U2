package com.illarilegal.cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ClienteRepository extends JpaRepository<ClienteEntity, Long> {
    Optional<ClienteEntity> findByKeycloakSub(String sub);
    boolean existsByKeycloakSub(String sub);
}
