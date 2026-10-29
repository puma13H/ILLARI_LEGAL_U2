package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "eureka.client.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:illari_orders_integration;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = {"illari.orden.creada", "illari.pago.resultado"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class OrdenPagoKafkaIntegrationTest {
    private static final String CLIENT_SUB = "integration-keycloak-sub";

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private OrdenRepository orders;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private org.springframework.kafka.config.KafkaListenerEndpointRegistry listeners;

    @BeforeEach
    void clearIsolatedDatabase() {
        // With latest offsets, publish only after assignment; otherwise this test races startup.
        listeners.getListenerContainers().forEach(container ->
                org.springframework.kafka.test.utils.ContainerTestUtils.waitForAssignment(container, 1));
        orders.deleteAll();
    }

    @Test
    void consumesApprovedAndRejectedPaymentEventsAndPersistsOrderStates() throws Exception {
        OrdenEntity approved = orders.save(pendingOrder());
        OrdenEntity rejected = orders.save(pendingOrder());

        publishResult(approved.getId(), "VALIDADO");
        publishResult(rejected.getId(), "FALLIDO");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(orders.findById(approved.getId()).orElseThrow().getEstado()).isEqualTo("PAGADA");
            assertThat(orders.findById(rejected.getId()).orElseThrow().getEstado()).isEqualTo("CANCELADA");
        });

        assertThat(orders.findByIdClienteOrderByFechaCreacionDesc(CLIENT_SUB))
                .extracting(OrdenEntity::getId)
                .contains(approved.getId(), rejected.getId());
    }

    private void publishResult(Long orderId, String state) throws Exception {
        String event = objectMapper.writeValueAsString(new PagoEventConsumer.PagoResultadoEvento(
                "VALIDADO".equals(state) ? "pago.validado" : "pago.fallido",
                orderId,
                CLIENT_SUB,
                new BigDecimal("25.00"),
                state,
                "pago-ms",
                Instant.now(), orderId + 1000, 1L));
        kafka.send("illari.pago.resultado", String.valueOf(orderId), event)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static OrdenEntity pendingOrder() {
        return new OrdenEntity(
                CLIENT_SUB,
                Instant.now(),
                "PENDIENTE_PAGO",
                new BigDecimal("25.00"),
                "SIMULACION",
                "[]");
    }
}
