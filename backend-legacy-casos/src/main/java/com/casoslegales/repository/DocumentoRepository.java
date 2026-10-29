package com.casoslegales.repository;

import com.casoslegales.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findByCasoIdOrderByIdDesc(Long casoId);
}
