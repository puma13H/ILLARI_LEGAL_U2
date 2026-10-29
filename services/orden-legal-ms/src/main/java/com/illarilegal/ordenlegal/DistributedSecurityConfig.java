package com.illarilegal.ordenlegal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import java.util.*;
@Configuration
public class DistributedSecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
      .requestMatchers("/actuator/health","/actuator/health/**","/actuator/info","/actuator/prometheus",
        "/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**").permitAll()
      .requestMatchers("/api/v1/ordenes/mensajeria/**").hasRole("ADMIN")
      .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/ordenes").hasAnyRole("CLIENTE","ADMIN")
      .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/**").hasRole("ADMIN")
      .requestMatchers(org.springframework.http.HttpMethod.PATCH,"/api/**").hasRole("ADMIN")
      // Existing read endpoints: require an application role in addition to their ownership checks.
      .requestMatchers(org.springframework.http.HttpMethod.GET,
          "/api/v1/ordenes/{id}", "/api/v1/ordenes/cliente/{idCliente}",
          "/api/v1/ordenes/resiliencia/servicio/{id}").hasAnyRole("ADMIN","ABOGADO","CLIENTE")
      .anyRequest().authenticated())
    .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter()))).build();
 }
 private JwtAuthenticationConverter converter() {
  JwtAuthenticationConverter c=new JwtAuthenticationConverter();
  c.setJwtGrantedAuthoritiesConverter(jwt->{
   List<org.springframework.security.core.GrantedAuthority> roles=new ArrayList<>();
   Object access=jwt.getClaim("realm_access");
   if(access instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> rs)
    for(Object role:rs) roles.add(new SimpleGrantedAuthority("ROLE_"+role.toString().toUpperCase(Locale.ROOT)));
   return roles;
  });return c;
 }
}
