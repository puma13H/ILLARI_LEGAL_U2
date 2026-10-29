package com.illarilegal.ordenlegal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface MensajeFallidoRepository extends JpaRepository<MensajeFallidoEntity,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MensajeFallidoEntity m where m.id=:id")
    Optional<MensajeFallidoEntity> bloquear(@Param("id") String id);
    @Query("select m.id from MensajeFallidoEntity m where m.estado='REPLAY_PENDIENTE' and (m.proximoIntento is null or m.proximoIntento<=:ahora) order by m.creado")
    List<String> pendientes(@Param("ahora") Instant ahora, org.springframework.data.domain.Pageable page);
    long countByEstado(String estado);
}

