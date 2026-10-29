package com.illarilegal.pago;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false", "illari.outbox.delay-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:cancel_regression;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class CancelacionPagoRegressionTest {
    @Autowired OrdenEventConsumer consumer;
    @Autowired PagoRepository payments;
    @Autowired PagoOutboxRepository outbox;
    @Autowired EventoRecibidoRepository recibidos;
    @MockBean KafkaTemplate<String, String> kafka;

    @BeforeEach void cleanIsolatedDatabase() { recibidos.deleteAll(); outbox.deleteAll(); payments.deleteAll(); }

    @Test void approvedPaymentIsRefundedWhenCancellationArrives() {
        consumer.onOrden(event("orden.creada", "client", "80.00"));
        assertThat(payments.findByIdOrden(1L).orElseThrow().getEstado()).isEqualTo("VALIDADO");
        consumer.onOrden(event("orden.cancelacion.solicitada", "client", "80.00"));
        assertThat(payments.findByIdOrden(1L).orElseThrow().getEstado()).isEqualTo("REEMBOLSADO");
        assertThat(outbox.findByOrdenId(1L).orElseThrow().getPayload()).contains("REEMBOLSADO");
    }

    @Test void cancellationBeforeCreationNeverApprovesThePayment() {
        consumer.onOrden(event("orden.cancelacion.solicitada", "client", "80.00"));
        consumer.onOrden(event("orden.creada", "client", "80.00"));
        assertThat(payments.findByIdOrden(1L).orElseThrow().getEstado()).isEqualTo("CANCELADO");
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test void repeatedCancellationAndCreationDoNotRepeatPaymentEffects() {
        consumer.onOrden(event("orden.creada", "client", "80.00"));
        for (int i = 0; i < 3; i++) {
            consumer.onOrden(event("orden.cancelacion.solicitada", "client", "80.00"));
            consumer.onOrden(event("orden.creada", "client", "80.00"));
        }
        assertThat(payments.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(payments.findByIdOrden(1L).orElseThrow().getEstado()).isEqualTo("REEMBOLSADO");
    }

    @Test void replayCannotChangeTheCustomerOrAmount() {
        consumer.onOrden(event("orden.creada", "client", "80.00"));
        assertThatThrownBy(() -> consumer.onOrden(event("orden.creada", "other", "80.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumer.onOrden(event("orden.creada", "client", "1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(payments.findByIdOrden(1L).orElseThrow().getMonto()).isEqualByComparingTo("80.00");
    }

    static String event(String type, String client, String amount) {
        return "{\"tipoEvento\":\"" + type + "\",\"ordenId\":1,\"idCliente\":\"" + client
                + "\",\"total\":" + amount + ",\"metodoPago\":\"YAPE\",\"origen\":\"orden-legal-ms\"}";
    }
}
