package com.casoslegales.repository;
import com.casoslegales.model.IdentidadKeycloak;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface IdentidadKeycloakRepository extends JpaRepository<IdentidadKeycloak,Long> {
    Optional<IdentidadKeycloak> findByIssuerAndSubject(String issuer,String subject);
    Optional<IdentidadKeycloak> findByUserIdAndIssuer(Long userId,String issuer);
}

