package com.illarilegal.pago;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "eureka.client.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:illari_payments_integration;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.kafka.consumer.auto-offset-reset=earliest"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = {"illari.orden.creada", "illari.pago.resultado"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class PagoKafkaIntegrationTest {
    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private PagoRepository payments;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void consumesAnOrderAndPersistsAndPublishesItsApprovedPayment() throws Exception {
        assertPublishedPayment("integration-approved-101", 101L, "YAPE", "VALIDADO", "pago.validado");
    }

    @Test
    void consumesAnOrderAndPersistsAndPublishesItsRejectedPayment() throws Exception {
        assertPublishedPayment(
                "integration-rejected-102", 102L, "TARJETA_RECHAZADA", "FALLIDO", "pago.fallido");
    }

    private void assertPublishedPayment(
            String key,
            Long orderId,
            String method,
            String expectedState,
            String expectedType) throws Exception {
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                consumerProperties(),
                new StringDeserializer(),
                new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "illari.pago.resultado");
        try {
            String event = "{\"tipoEvento\":\"orden.creada\",\"ordenId\":" + orderId
                    + ",\"idCliente\":\"integration-client-sub\",\"total\":45.00"
                    + ",\"metodoPago\":\"" + method + "\",\"origen\":\"orden-legal-ms\"}";
            kafka.send("illari.orden.creada", key, event).get(10, TimeUnit.SECONDS);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(payments.findByIdOrden(orderId))
                            .get()
                            .extracting(PagoEntity::getEstado)
                            .isEqualTo(expectedState));

            ConsumerRecord<String, String> result = pollForOrder(consumer, orderId);
            assertThat(result).isNotNull();
            OrdenEventConsumer.ResultadoPagoEvento payload =
                    objectMapper.readValue(
                            result.value(), OrdenEventConsumer.ResultadoPagoEvento.class);
            assertThat(result.key()).isEqualTo(String.valueOf(orderId));
            assertThat(payload.ordenId()).isEqualTo(orderId);
            assertThat(payload.idCliente()).isEqualTo("integration-client-sub");
            assertThat(payload.estado()).isEqualTo(expectedState);
            assertThat(payload.tipoEvento()).isEqualTo(expectedType);
        } finally {
            consumer.close(Duration.ofSeconds(2));
        }
    }

    private ConsumerRecord<String, String> pollForOrder(Consumer<String, String> consumer, Long orderId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (String.valueOf(orderId).equals(record.key())) return record;
            }
        }
        return null;
    }

    private Map<String, Object> consumerProperties() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                "integration-results-" + UUID.randomUUID(), "false", broker);
        properties.put("auto.offset.reset", "earliest");
        return properties;
    }
}
