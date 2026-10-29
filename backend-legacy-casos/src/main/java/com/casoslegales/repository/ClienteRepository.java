package com.casoslegales.repository;

import com.casoslegales.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByUserId(Long userId);
    Optional<Cliente> findByEmail(String email);
}
