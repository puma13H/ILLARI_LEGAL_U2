package com.casoslegales.config;
import com.casoslegales.model.*;
import com.casoslegales.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;

class DataInitializerTest {
    @Test void repeatedStartupNeverOverwritesExistingCredentialsOrRoles() {
        var roles=mock(RoleRepository.class);var users=mock(UserRepository.class);
        var lawyers=mock(AbogadoRepository.class);var clients=mock(ClienteRepository.class);
        var cases=mock(CasoRepository.class);var encoder=mock(PasswordEncoder.class);
        for(String role:List.of("admin","abogado","cliente")){
            when(roles.findByName(role)).thenReturn(Optional.of(new Role()));
            var user=new User();user.setId(7L);
            when(users.findByEmail(role+"@casoslegales.com")).thenReturn(Optional.of(user));
        }
        when(lawyers.findByUserId(7L)).thenReturn(Optional.of(new Abogado()));
        when(clients.findByUserId(7L)).thenReturn(Optional.of(new Cliente()));
        when(cases.count()).thenReturn(1L);
        var initializer=new DataInitializer(roles,users,lawyers,clients,cases,encoder);
        for(String field:List.of("demoAdminPassword","demoAbogadoPassword","demoClientePassword"))
            ReflectionTestUtils.setField(initializer,field,UUID.randomUUID().toString());
        initializer.run();
        verify(users,never()).save(any());verifyNoInteractions(encoder);
    }
}

