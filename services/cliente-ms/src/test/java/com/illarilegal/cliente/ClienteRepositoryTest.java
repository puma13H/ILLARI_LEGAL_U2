package com.illarilegal.cliente;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
class ClienteRepositoryTest {
    @Autowired ClienteRepository repo;

    @Test
    void storesKeycloakSubjectDurablyInsteadOfInMemoryMap() {
        ClienteEntity cliente = repo.save(new ClienteEntity("12345678", "Ana", "Pérez",
                "ana@example.test", "999111222", "Juliaca", "ACTIVO", "kc-sub-client-01"));
        assertThat(repo.findByKeycloakSub("kc-sub-client-01")).isPresent()
                .get().extracting(ClienteEntity::getId).isEqualTo(cliente.getId());
    }
}
