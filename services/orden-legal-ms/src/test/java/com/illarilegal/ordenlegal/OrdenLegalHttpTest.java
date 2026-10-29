package com.illarilegal.ordenlegal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrdenLegalController.class)
@Import(DistributedSecurityConfig.class)
class OrdenLegalHttpTest {
    private static final String SUBJECT = "keycloak-user-uuid-7f93";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrdenRepository repository;

    @MockBean
    private KafkaTemplate<String, String> kafka;
    @MockBean
    private CatalogoResilienteService catalogoResiliente;
    @MockBean
    private OrdenOutboxRepository outbox;

    @Test
    void clientCannotForceOrderToPaidThroughPatch() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/ordenes/23/estado")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"PAGADA\"}")
                .with(jwt().jwt(token -> token.subject(SUBJECT)
                        .claim("realm_access", Map.of("roles", List.of("CLIENTE")))).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsOrdersForTheExactKeycloakSubjectOverHttp() throws Exception {
        when(repository.findByIdClienteOrderByFechaCreacionDesc(SUBJECT)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ordenes/cliente/{subject}", SUBJECT)
                        .with(jwt().jwt(token -> token
                                .subject(SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("CLIENTE")))).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(repository).findByIdClienteOrderByFechaCreacionDesc(SUBJECT);
    }

    @Test
    void returnsForbiddenRatherThanBadRequestForAnotherSubject() throws Exception {
        mockMvc.perform(get("/api/v1/ordenes/cliente/{subject}", "different-subject")
                        .with(jwt().jwt(token -> token
                                .subject(SUBJECT)
                                .claim("realm_access", Map.of("roles", List.of("CLIENTE")))).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }
}
