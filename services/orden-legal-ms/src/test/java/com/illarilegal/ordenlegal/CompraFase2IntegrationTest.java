package com.illarilegal.ordenlegal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false","spring.kafka.admin.auto-create=false","illari.outbox.delay-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:compra_fase2;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class CompraFase2IntegrationTest {
    @Autowired CompraIdempotenteService compras;
    @Autowired CompraIdempotenciaRepository claves;
    @Autowired OrdenRepository orders;
    @Autowired OrdenLegalController controller;
    @SpyBean OrdenOutboxRepository outbox;
    @Autowired EventoRecibidoRepository recibidos;
    @Autowired MensajeFallidoRepository fallidos;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc http;
    @Autowired OrdenOutboxPublisher publisher;
    @Autowired MeterRegistry metrics;
    @MockBean CatalogoResilienteService catalogo;
    @MockBean KafkaTemplate<String,String> kafka;

    @BeforeEach void prepararBaseAislada() {
        reset(outbox,kafka,catalogo);
        claves.deleteAll();recibidos.deleteAll();fallidos.deleteAll();outbox.deleteAll();orders.deleteAll();
        when(catalogo.obtener(anyLong())).thenAnswer(i->new OrdenLegalController.ServicioLegal(i.getArgument(0),
                "Nombre histórico", "",new BigDecimal("50.00"),"LEGAL","ACTIVO"));
    }
    static Jwt cliente(String subject) {
        return Jwt.withTokenValue("test").header("alg","none").subject(subject)
                .claim("realm_access",Map.of("roles",List.of("CLIENTE"))).build();
    }
    static OrdenLegalController.NuevaOrden carrito() {
        return new OrdenLegalController.NuevaOrden("Yape",List.of(new OrdenLegalController.DetalleNuevaOrden(7L,2),
                new OrdenLegalController.DetalleNuevaOrden(8L,1)));
    }
    @Test void repeatedAndConcurrentPurchasesAcrossServiceInstancesCreateOneOrder() throws Exception {
        var second=new CompraIdempotenteService(claves,controller,mapper,manager);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var gate=new CountDownLatch(1); List<Future<OrdenLegalController.OrdenLegal>> tasks=new ArrayList<>();
            for(int i=0;i<16;i++){var instance=i%2==0?compras:second;tasks.add(pool.submit(()->{gate.await();return instance.crear("checkout-concurrente",carrito(),cliente("client"));}));}
            gate.countDown(); Set<Long> ids=new HashSet<>();
            for(var f:tasks)ids.add(f.get(30,TimeUnit.SECONDS).id());
            assertThat(ids).hasSize(1);
        }
        assertThat(orders.count()).isEqualTo(1);assertThat(outbox.count()).isEqualTo(1);assertThat(claves.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from orden_detalle",Long.class)).isEqualTo(2);
    }
    @Test void sameKeyDifferentPayloadReturns409AndOtherCustomerHasIndependentScope() {
        var first=compras.crear("checkout-fixed",carrito(),cliente("a"));
        var changed=new OrdenLegalController.NuevaOrden("PLIN",carrito().detalles());
        assertThatThrownBy(()->compras.crear("checkout-fixed",changed,cliente("a"))).isInstanceOfSatisfying(
                ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(compras.crear("checkout-fixed",carrito(),cliente("b")).id()).isNotEqualTo(first.id());
    }
    @Test void snapshotAndResponseSurvivePriceChangesAndRelateTheSameServiceToSeveralOrders() {
        var first=compras.crear("checkout-snapshot",carrito(),cliente("a"));
        when(catalogo.obtener(anyLong())).thenReturn(new OrdenLegalController.ServicioLegal(7L,"Nuevo", "",new BigDecimal("999.00"),"LEGAL","ACTIVO"));
        assertThat(compras.crear("checkout-snapshot",carrito(),cliente("a"))).isEqualTo(first);
        assertThat(jdbc.queryForObject("select nombre_historico from orden_detalle where orden_id=? and servicio_id=7",String.class,first.id())).isEqualTo("Nombre histórico");
        reset(catalogo);when(catalogo.obtener(anyLong())).thenAnswer(i->new OrdenLegalController.ServicioLegal(i.getArgument(0),"Segundo", "",BigDecimal.TEN,"LEGAL","ACTIVO"));
        compras.crear("checkout-new-order",carrito(),cliente("a"));
        assertThat(jdbc.queryForObject("select count(distinct orden_id) from orden_detalle where servicio_id=7",Long.class)).isEqualTo(2);
    }
    @Test void failedOutboxWriteRollsBackOrderAndDetailsAndTheSameKeyCanRetry() {
        doThrow(new IllegalStateException("forced failure")).when(outbox).save(any());
        assertThatThrownBy(()->compras.crear("checkout-rollback",carrito(),cliente("a"))).isInstanceOf(IllegalStateException.class);
        assertThat(orders.count()).isZero();assertThat(jdbc.queryForObject("select count(*) from orden_detalle",Long.class)).isZero();
        reset(outbox);
        compras.crear("checkout-rollback",carrito(),cliente("a"));assertThat(orders.count()).isEqualTo(1);
    }
    @Test void historicalJsonIsStillReadableWithoutDiscardingIt() throws Exception {
        var d=List.of(new OrdenLegalController.DetalleOrden(7L,"Histórico",2,BigDecimal.TEN,new BigDecimal("20.00")));
        var entity=orders.save(new OrdenEntity("a",Instant.now(),"PAGADA",new BigDecimal("20.00"),"YAPE",mapper.writeValueAsString(d)));
        assertThat(controller.obtener(entity.getId(),cliente("a")).getBody().detalles()).isEqualTo(d);
        assertThat(orders.findById(entity.getId()).orElseThrow().getDetallesJson()).contains("Histórico");
    }
    @Test void httpRequiresKeyReplaysResponseAndRejectsChangingPayload() throws Exception {
        var token=jwt().jwt(j->j.subject("http-client").claim("realm_access",Map.of("roles",List.of("CLIENTE"))))
                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
        http.perform(post("/api/v1/ordenes").with(token).contentType("application/json").content(mapper.writeValueAsString(carrito()))).andExpect(status().isBadRequest());
        String first=http.perform(post("/api/v1/ordenes").with(token).header("Idempotency-Key","http-key-123")
                .contentType("application/json").content(mapper.writeValueAsString(carrito()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        http.perform(post("/api/v1/ordenes").with(token).header("Idempotency-Key","http-key-123")
                .contentType("application/json").content(mapper.writeValueAsString(carrito()))).andExpect(status().isOk()).andExpect(content().json(first));
        http.perform(post("/api/v1/ordenes").with(token).header("Idempotency-Key","http-key-123")
                .contentType("application/json").content(mapper.writeValueAsString(new OrdenLegalController.NuevaOrden("PLIN",carrito().detalles()))))
                .andExpect(status().isConflict());
    }
    @Test void clientCannotImpersonateAndAdminCanExplicitlyBuyForAnotherCustomer() throws Exception {
        var body=new OrdenLegalController.NuevaOrden("YAPE",carrito().detalles(),"other-client");
        http.perform(post("/api/v1/ordenes").with(jwt().jwt(j->j.subject("client")).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                .header("Idempotency-Key","impersonation-key").contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        assertThat(orders.count()).isZero();
        http.perform(post("/api/v1/ordenes").with(jwt().jwt(j->j.subject("admin").claim("realm_access",Map.of("roles",List.of("ADMIN"))))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .header("Idempotency-Key","admin-client-key").contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idCliente").value("other-client"));
    }
    @Test void clientCannotReplayDeadLetters() throws Exception {
        http.perform(post("/api/v1/ordenes/mensajeria/fallidos/unknown/reprocesar")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                .contentType("application/json").content("{\"motivo\":\"prueba\"}")).andExpect(status().isForbidden());
    }
    @Test void lawyerCannotCreatePurchasesAndJwtWithoutApplicationRolesCannotReadThem() throws Exception {
        http.perform(post("/api/v1/ordenes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ABOGADO")))
                .header("Idempotency-Key","lawyer-purchase-key").contentType("application/json")
                .content(mapper.writeValueAsString(carrito()))).andExpect(status().isForbidden());
        http.perform(get("/api/v1/ordenes/1").with(jwt().authorities(java.util.List.of())))
                .andExpect(status().isForbidden());
        assertThat(orders.count()).isZero();
    }
    @Test void outboxContinuesAfterFailureUsesBackoffAndStopsAfterFiveAttempts() {
        var bad=outbox.save(new OrdenOutboxEntity(1L,"{}"));
        var good=outbox.save(new OrdenOutboxEntity(2L,"{}"));
        when(kafka.send(anyString(),anyString(),anyString())).thenAnswer(i->"1".equals(i.getArgument(1))
                ? CompletableFuture.failedFuture(new org.apache.kafka.common.KafkaException("offline")) : CompletableFuture.completedFuture(null));
        publisher.publicarPendientes();
        assertThat(outbox.findById(good.getId()).orElseThrow().isPublicado()).isTrue();
        var retry=outbox.findById(bad.getId()).orElseThrow();
        assertThat(retry.getIntentos()).isEqualTo(1);assertThat(retry.getProximoIntento()).isAfter(Instant.now());
        for(int i=1;i<5;i++) {
            new TransactionTemplate(manager).executeWithoutResult(s->{var e=outbox.bloquear(bad.getId()).orElseThrow();
                org.springframework.test.util.ReflectionTestUtils.setField(e,"proximoIntento",Instant.now().minusSeconds(1));});
            publisher.publicarPendientes();
        }
        assertThat(outbox.findById(bad.getId()).orElseThrow().isFallido()).isTrue();
        assertThat(outbox.findById(bad.getId()).orElseThrow().getIntentos()).isEqualTo(5);
        publisher.publicarPendientes();
        assertThat(outbox.findById(bad.getId()).orElseThrow().getIntentos()).isEqualTo(5);
        assertThat(metrics.get("illari.outbox.fallidos").tag("servicio","orden").gauge().value()).isEqualTo(1);
    }
}
