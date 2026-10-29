package com.illarilegal.pago;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<PagoEntity, Long> {
    long countByEstado(String estado);
    Optional<PagoEntity> findByIdOrden(Long idOrden);
    List<PagoEntity> findAllByIdOrdenOrderByFechaCreacionAsc(Long idOrden);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagoEntity p where p.idOrden = :idOrden")
    Optional<PagoEntity> findByOrdenForUpdate(@Param("idOrden") Long idOrden);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagoEntity p where p.id = :id")
    Optional<PagoEntity> findForUpdate(@Param("id") Long id);
}
