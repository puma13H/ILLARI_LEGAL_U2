package com.casoslegales.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.casoslegales.security.IdentidadJuridicaFilter;
import com.casoslegales.repository.IdentidadKeycloakRepository;
import java.util.*;

@Configuration
@ConditionalOnProperty(name="illari.keycloak-legacy-enabled",havingValue="true")
public class KeycloakLegacySecurity {
    @Bean @Order(1)
    SecurityFilterChain keycloakApi(HttpSecurity http,IdentidadKeycloakRepository identidades) throws Exception {
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt->{
            Object access=jwt.getClaim("realm_access");
            Collection<?> roles=access instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> r?r:List.of();
            return List.of("admin","abogado","cliente").stream()
                    .filter(role->roles.stream().anyMatch(r->role.equalsIgnoreCase(r.toString())))
                    .map(role->(org.springframework.security.core.GrantedAuthority)new SimpleGrantedAuthority("ROLE_"+role)).toList();
        });
        return http.csrf(c->c.disable()).cors(c->{})
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth->auth
                // Internal error dispatch retains the original 4xx/5xx. Direct /error requests remain denied.
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health","/actuator/health/**","/actuator/info","/actuator/prometheus").permitAll()
                .requestMatchers("/api/auth/**","/h2-console/**").denyAll()
                .requestMatchers("/api/identidades/vinculos").hasRole("admin")
                .requestMatchers("/api/admin/**","/api/agenda/**").hasRole("admin")
                .requestMatchers("/api/abogados/**").hasAnyRole("admin","abogado")
                .requestMatchers("/api/clientes/**").hasAnyRole("admin","abogado")
                .requestMatchers("/api/casos/**","/api/documentos/**","/api/dashboard").hasAnyRole("admin","abogado","cliente")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)))
            .addFilterAfter(new IdentidadJuridicaFilter(identidades),
                    org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .build();
    }
}
