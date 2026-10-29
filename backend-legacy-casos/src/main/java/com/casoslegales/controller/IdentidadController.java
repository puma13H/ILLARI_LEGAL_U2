package com.casoslegales.controller;
import com.casoslegales.security.IdentidadJuridicaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

@RestController @RequestMapping("/api/identidades")
@ConditionalOnProperty(name="illari.keycloak-legacy-enabled",havingValue="true")
public class IdentidadController {
    private final IdentidadJuridicaService service;
    public IdentidadController(IdentidadJuridicaService service){this.service=service;}
    public record Vinculo(Long userId,String subject){}
    @PostMapping("/vinculos") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vincular(@RequestBody Vinculo request,Authentication auth){
        service.vincular(request.userId(),request.subject(),auth.getName());
    }
}

