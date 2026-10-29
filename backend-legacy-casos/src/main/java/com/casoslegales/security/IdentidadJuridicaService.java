package com.casoslegales.security;
import com.casoslegales.model.*;
import com.casoslegales.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class IdentidadJuridicaService {
    private final IdentidadKeycloakRepository identidades;
    private final UserRepository usuarios;
    private final String issuer;
    public IdentidadJuridicaService(IdentidadKeycloakRepository identidades,UserRepository usuarios,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        this.identidades=identidades;this.usuarios=usuarios;this.issuer=issuer;
    }
    @Transactional
    public void vincular(Long userId,String subject,String operador) {
        if(userId==null || subject==null || subject.isBlank() || subject.length()>255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Usuario y subject son obligatorios.");
        User user=usuarios.findForUpdate(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var actual=identidades.findByUserIdAndIssuer(userId,issuer);
        var otro=identidades.findByIssuerAndSubject(issuer,subject);
        if(actual.isPresent() && actual.get().getSubject().equals(subject))return;
        if(actual.isPresent() || otro.isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"La identidad ya está vinculada; no se reemplaza automáticamente.");
        identidades.saveAndFlush(new IdentidadKeycloak(issuer,subject,user,operador));
    }
}

