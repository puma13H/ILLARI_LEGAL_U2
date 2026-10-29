package com.casoslegales.repository;

import com.casoslegales.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByCasoIdOrderByIdAsc(Long casoId);
}
