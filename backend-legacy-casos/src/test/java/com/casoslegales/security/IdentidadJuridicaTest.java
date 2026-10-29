package com.casoslegales.security;
import com.casoslegales.model.*;
import com.casoslegales.repository.*;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class IdentidadJuridicaTest {
    IdentidadKeycloakRepository repo;
    UserRepository users;
    IdentidadJuridicaFilter filter;
    final String issuer="http://issuer.test/realms/illari-legal";
    @BeforeEach void setup(){repo=mock(IdentidadKeycloakRepository.class);users=mock(UserRepository.class);filter=new IdentidadJuridicaFilter(repo);}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    User user(){var role=new Role();role.setName("cliente");var u=new User();u.setId(7L);u.setEmail("local-contact@example.test");u.setRole(role);return u;}
    void token(String subject,String role){
        var jwt=Jwt.withTokenValue("test-only").header("alg","none").issuer(issuer).subject(subject)
                .claim("email","different-contact@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @Test void issuerAndSubjectResolveTheLocalIdentityWithoutUsingTokenEmail() throws Exception {
        token("stable-sub","cliente");
        when(repo.findByIssuerAndSubject(issuer,"stable-sub")).thenReturn(Optional.of(new IdentidadKeycloak(issuer,"stable-sub",user(),"admin")));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        var request=new MockHttpServletRequest("GET","/api/casos");request.setServletPath("/api/casos");
        filter.doFilter(request,response,chain);
        verify(chain).doFilter(request,response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("local-contact@example.test");
    }
    @Test void missingBindingIsForbiddenRatherThanAutomaticallyLinkingByEmail() throws Exception {
        token("unlinked","cliente");
        var request=new MockHttpServletRequest("GET","/api/casos");request.setServletPath("/api/casos");
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        filter.doFilter(request,response,chain);assertThat(response.getStatus()).isEqualTo(403);verifyNoInteractions(chain);
    }
    @Test void tokenRoleMustMatchTheLinkedLegalRole() throws Exception {
        token("stable-sub","abogado");
        when(repo.findByIssuerAndSubject(issuer,"stable-sub")).thenReturn(Optional.of(new IdentidadKeycloak(issuer,"stable-sub",user(),"admin")));
        var request=new MockHttpServletRequest("GET","/api/casos");request.setServletPath("/api/casos");
        var response=new MockHttpServletResponse();filter.doFilter(request,response,mock(FilterChain.class));
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void linkingDoesNotModifyUserCredentials() {
        var local=user();when(users.findForUpdate(7L)).thenReturn(Optional.of(local));
        var service=new IdentidadJuridicaService(repo,users,issuer);service.vincular(7L,"stable-sub","admin-sub");
        verify(repo).saveAndFlush(any(IdentidadKeycloak.class));verify(users,never()).save(any());assertThat(local.getPassword()).isNull();
    }
    @Test void repeatedIdenticalBindingIsIdempotent() {
        var local=user();when(users.findForUpdate(7L)).thenReturn(Optional.of(local));
        when(repo.findByUserIdAndIssuer(7L,issuer)).thenReturn(Optional.of(new IdentidadKeycloak(issuer,"stable-sub",local,"admin")));
        new IdentidadJuridicaService(repo,users,issuer).vincular(7L,"stable-sub","admin-sub");
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void existingBindingCannotBeReplacedSilently() {
        var local=user();when(users.findForUpdate(7L)).thenReturn(Optional.of(local));
        when(repo.findByUserIdAndIssuer(7L,issuer)).thenReturn(Optional.of(new IdentidadKeycloak(issuer,"old-sub",local,"admin")));
        assertThatThrownBy(()->new IdentidadJuridicaService(repo,users,issuer).vincular(7L,"other-sub","admin-sub"))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(repo,never()).saveAndFlush(any());
    }
}

