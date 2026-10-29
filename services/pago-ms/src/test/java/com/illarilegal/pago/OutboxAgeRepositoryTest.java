package com.illarilegal.pago;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.cloud.config.enabled=false","spring.config.import="})
class OutboxAgeRepositoryTest {
    @Autowired PagoOutboxRepository outbox;
    @Test void ageIncludesExhaustedUntilConfirmedPublication() {
        var old=new PagoOutboxEntity(90001L,"{\"ordenId\":90001}");
        Instant oldest=Instant.now().minusSeconds(90).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        ReflectionTestUtils.setField(old,"creado",oldest);
        for(int i=0;i<5;i++)old.fallo(new IllegalStateException());
        var recent=new PagoOutboxEntity(90002L,"{\"ordenId\":90002}");
        Instant newer=oldest.plusSeconds(60);ReflectionTestUtils.setField(recent,"creado",newer);
        outbox.saveAndFlush(old);outbox.saveAndFlush(recent);
        assertEquals(1,outbox.contarPendientes());assertEquals(1,outbox.countByFallidoTrue());
        assertEquals(oldest,outbox.masAntiguoPendiente());
        old.setPublicado(true);outbox.saveAndFlush(old);assertEquals(newer,outbox.masAntiguoPendiente());
        recent.setPublicado(true);outbox.saveAndFlush(recent);assertNull(outbox.masAntiguoPendiente());
    }
}
