package com.illarilegal.gateway;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Mono;

@Configuration
public class GatewaySecurity {

    private Mono<AbstractAuthenticationToken> convertirRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");

        List<GrantedAuthority> authorities =
            roles instanceof Collection<?> lista
                ? lista.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))
                    .toList()
                : List.of();

        return Mono.just(new JwtAuthenticationToken(jwt, authorities));
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> {})
            .authorizeExchange(ex -> ex
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/servicios", "/api/v1/servicios/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/servicios", "/api/v1/servicios/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.PUT, "/api/v1/servicios/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.PATCH, "/api/v1/servicios/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.DELETE, "/api/v1/servicios/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.PATCH, "/api/v1/ordenes/**").hasRole("ADMIN")
                .pathMatchers("/api/v1/ordenes/mensajeria/**", "/api/v1/pagos/mensajeria/**", "/api/v1/pagos/webhook").hasRole("ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/v1/ordenes").hasAnyRole("CLIENTE", "ADMIN")
                .pathMatchers("/api/v1/ordenes/**").hasAnyRole("CLIENTE", "ADMIN", "ABOGADO")
                .pathMatchers("/api/v1/pagos/**").hasAnyRole("CLIENTE", "ADMIN", "ABOGADO")
                .pathMatchers(HttpMethod.GET, "/api/v1/clientes/me").hasAnyRole("ADMIN", "ABOGADO", "CLIENTE")
                .pathMatchers(HttpMethod.POST, "/api/v1/clientes/me").hasRole("CLIENTE")
                .pathMatchers(HttpMethod.GET, "/api/v1/clientes", "/api/v1/clientes/**").hasAnyRole("ADMIN", "ABOGADO")
                .pathMatchers("/api/v1/clientes", "/api/v1/clientes/**").hasRole("ADMIN")
                .pathMatchers("/api/auth/**").denyAll()
                .pathMatchers("/api/identidades/vinculos", "/api/agenda/**", "/api/admin/**").hasRole("ADMIN")
                .pathMatchers("/api/abogados/**", "/api/clientes/**").hasAnyRole("ADMIN", "ABOGADO")
                .pathMatchers(HttpMethod.POST, "/api/casos").hasAnyRole("ADMIN", "ABOGADO")
                .pathMatchers("/api/casos/**", "/api/documentos/**", "/api/dashboard").hasAnyRole("ADMIN", "ABOGADO", "CLIENTE")
                .anyExchange().denyAll()
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(this::convertirRoles))
            )
            .build();
    }
}
