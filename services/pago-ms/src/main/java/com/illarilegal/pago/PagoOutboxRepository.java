package com.illarilegal.pago;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PagoOutboxRepository extends JpaRepository<PagoOutboxEntity, Long> {
    @org.springframework.data.jpa.repository.Query("select e.id from PagoOutboxEntity e where e.publicado=false and coalesce(e.fallido,false)=false and (e.proximoIntento is null or e.proximoIntento<=:ahora) order by e.id")
    java.util.List<Long> pendientes(@org.springframework.data.repository.query.Param("ahora") java.time.Instant ahora, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from PagoOutboxEntity e where e.id=:id")
    java.util.Optional<PagoOutboxEntity> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select count(e) from PagoOutboxEntity e where e.publicado=false and coalesce(e.fallido,false)=false")
    long contarPendientes();
    @org.springframework.data.jpa.repository.Query("select min(e.creado) from PagoOutboxEntity e where e.publicado=false")
    java.time.Instant masAntiguoPendiente();
    long countByFallidoTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PagoOutboxEntity> findTop20ByPublicadoFalseOrderByIdAsc();
    Optional<PagoOutboxEntity> findByOrdenId(Long ordenId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from PagoOutboxEntity e where e.ordenId = :ordenId")
    Optional<PagoOutboxEntity> findForUpdate(@org.springframework.data.repository.query.Param("ordenId") Long ordenId);
}
