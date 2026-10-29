package com.illarilegal.ordenlegal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CompraIdempotenciaRepository extends JpaRepository<CompraIdempotenciaEntity,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompraIdempotenciaEntity c where c.id=:id")
    Optional<CompraIdempotenciaEntity> bloquear(@Param("id") String id);
}

