package com.casoslegales.repository;

import com.casoslegales.model.Caso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CasoRepository extends JpaRepository<Caso, Long> {
    List<Caso> findByAbogadoIdOrderByIdDesc(Long abogadoId);
    List<Caso> findByClienteIdOrderByIdDesc(Long clienteId);
}
