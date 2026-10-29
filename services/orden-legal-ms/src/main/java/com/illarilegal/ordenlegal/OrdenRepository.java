package com.illarilegal.ordenlegal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface OrdenRepository extends JpaRepository<OrdenEntity, Long> {
    long countByEstado(String estado);
    List<OrdenEntity> findByIdClienteOrderByFechaCreacionDesc(String idCliente);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenEntity o where o.id = :id")
    java.util.Optional<OrdenEntity> findForUpdate(@Param("id") Long id);
    boolean existsByPagoIdAndIdNot(Long pagoId, Long id);
}
