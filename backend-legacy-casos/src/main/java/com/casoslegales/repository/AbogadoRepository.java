package com.casoslegales.repository;

import com.casoslegales.model.Abogado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AbogadoRepository extends JpaRepository<Abogado, Long> {
    Optional<Abogado> findByUserId(Long userId);
    Optional<Abogado> findByEmail(String email);
}
