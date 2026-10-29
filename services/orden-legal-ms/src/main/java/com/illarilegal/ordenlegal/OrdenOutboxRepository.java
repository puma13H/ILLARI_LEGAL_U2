package com.illarilegal.ordenlegal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrdenOutboxRepository extends JpaRepository<OrdenOutboxEntity, Long> {
    @org.springframework.data.jpa.repository.Query("select e.id from OrdenOutboxEntity e where e.publicado=false and coalesce(e.fallido,false)=false and (e.proximoIntento is null or e.proximoIntento<=:ahora) order by e.id")
    java.util.List<Long> pendientes(@org.springframework.data.repository.query.Param("ahora") java.time.Instant ahora, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from OrdenOutboxEntity e where e.id=:id")
    java.util.Optional<OrdenOutboxEntity> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select count(e) from OrdenOutboxEntity e where e.publicado=false and coalesce(e.fallido,false)=false")
    long contarPendientes();
    @org.springframework.data.jpa.repository.Query("select min(e.creado) from OrdenOutboxEntity e where e.publicado=false")
    java.time.Instant masAntiguoPendiente();
    long countByFallidoTrue();
    List<OrdenOutboxEntity> findTop20ByPublicadoFalseOrderByIdAsc();
}
