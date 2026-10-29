package com.illarilegal.serviciolegal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicioLegalRepository extends JpaRepository<ServicioLegalEntity, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
}
