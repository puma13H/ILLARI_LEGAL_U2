package com.illarilegal.verification;

import com.illarilegal.ordenlegal.*;
import com.illarilegal.pago.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.jwt.Jwt;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Two real Spring transactions/data stores and Kafka. No HTTP, Keycloak, Docker or user database. */
class SagaOrdenPagoIntegrationTest {
    static EmbeddedKafkaKraftBroker broker;
    static ConfigurableApplicationContext ordersContext, paymentsContext;
    static OrdenLegalController orders;
    static OrdenRepository orderRepo;
    static PagoRepository paymentRepo;
    static OrdenOutboxRepository orderOutbox;
    static KafkaTemplate<String, String> kafka;
    static ObjectMapper mapper;
    static Long beforePaymentConsumer;
    static final java.util.concurrent.ConcurrentMap<Long,java.util.concurrent.atomic.AtomicInteger> fallos = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentMap<Long,java.util.List<Long>> tiempos = new java.util.concurrent.ConcurrentHashMap<>();

    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration @EnableScheduling
    @EntityScan(basePackageClasses = OrdenEntity.class)
    @EnableJpaRepositories(basePackageClasses = OrdenRepository.class)
    @Import({OrdenLegalController.class, PagoEventConsumer.class, OrdenOutboxPublisher.class, CompraIdempotenteService.class,
            com.illarilegal.ordenlegal.KafkaRecoveryConfiguration.class, com.illarilegal.ordenlegal.MensajeriaRecoveryService.class})
    static class OrdersConfig {
        @Bean CatalogoResilienteService catalogo() {
            return new CatalogoResilienteService(id -> new OrdenLegalController.ServicioLegal(id,
                    "Servicio " + id, "", new BigDecimal(id == 7L ? "50.00" : "20.00"), "LEGAL", "ACTIVO"));
        }
    }

    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration @EnableScheduling
    @EntityScan(basePackageClasses = PagoEntity.class)
    @EnableJpaRepositories(basePackageClasses = PagoRepository.class)
    @Import({PagoService.class, OrdenEventConsumer.class, PagoOutboxPublisher.class,
            com.illarilegal.pago.KafkaRecoveryConfiguration.class, com.illarilegal.pago.MensajeriaRecoveryService.class})
    static class PaymentsConfig {
        @Bean @Primary PagoService pagosConFalloControlado(PagoRepository repo) {
            return new PagoService(repo) {
                @Override public Pago iniciarDesdeOrden(Long id,String cliente,BigDecimal monto) {
                    if(fallos.containsKey(id)) {
                        tiempos.computeIfAbsent(id,k->new java.util.concurrent.CopyOnWriteArrayList<>()).add(System.nanoTime());
                        if(fallos.get(id).getAndDecrement()>0)
                            throw new org.springframework.dao.TransientDataAccessResourceException("fallo temporal de prueba");
                    }
                    return super.iniciarDesdeOrden(id,cliente,monto);
                }
            };
        }
    }

    @BeforeAll static void startIsolatedSystem() {
        broker = new EmbeddedKafkaKraftBroker(1, 1, "illari.orden.creada", "illari.pago.resultado",
                "illari.orden.creada.DLT", "illari.pago.resultado.DLT");
        try(var socket=new java.net.ServerSocket(0)){broker.kafkaPorts(socket.getLocalPort());}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
        broker.afterPropertiesSet();
        ordersContext = start(OrdersConfig.class, "orders");
        orders = ordersContext.getBean(OrdenLegalController.class);
        orderRepo = ordersContext.getBean(OrdenRepository.class);
        beforePaymentConsumer=create("YAPE").id();
        ordersContext.getBean(OrdenOutboxPublisher.class).publicarPendientes();
        paymentsContext = start(PaymentsConfig.class, "payments");
        orders = ordersContext.getBean(OrdenLegalController.class);
        orderRepo = ordersContext.getBean(OrdenRepository.class);
        paymentRepo = paymentsContext.getBean(PagoRepository.class);
        orderOutbox = ordersContext.getBean(OrdenOutboxRepository.class);
        kafka = ordersContext.getBean(KafkaTemplate.class);
        mapper = ordersContext.getBean(ObjectMapper.class);
    }

    static ConfigurableApplicationContext start(Class<?> config, String name, String... extras) {
        String pg=System.getProperty("fase2.postgres");
        java.util.List<String> args=new java.util.ArrayList<>(java.util.List.of(
                "--spring.application.name=saga-test-" + name,
                "--spring.cloud.config.enabled=false", "--spring.cloud.config.import-check.enabled=false",
                "--eureka.client.enabled=false", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--spring.datasource.url="+(pg==null?"jdbc:h2:mem:saga_"+name+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000":pg+"saga_"+name),
                "--spring.datasource.username="+(pg==null?"sa":"postgres"), "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto="+(pg==null?"create-drop":"validate"),
                "--spring.kafka.bootstrap-servers=" + broker.getCluster().bootstrapServers(),
                "--spring.kafka.consumer.auto-offset-reset=earliest", "--spring.kafka.consumer.enable-auto-commit=false",
                "--spring.kafka.producer.properties.max.block.ms=1000",
                "--spring.kafka.producer.properties.request.timeout.ms=1000",
                "--spring.kafka.producer.properties.delivery.timeout.ms=5000",
                "--illari.outbox.delay-ms=100"));
        for(String extra:extras) {
            String key=extra.substring(0,extra.indexOf('=')+1);
            args.removeIf(existing->existing.startsWith(key));args.add(extra);
        }
        return new SpringApplicationBuilder(config).web(WebApplicationType.NONE).run(args.toArray(String[]::new));
    }

    @AfterAll static void stopOnlyTestResources() {
        if (paymentsContext != null) paymentsContext.close();
        if (ordersContext != null) ordersContext.close();
        if (broker != null) broker.destroy();
    }

    @Test void multiServicePurchaseAndRejectedPaymentConvergeThroughKafka() {
        var approved = create("YAPE");
        assertThat(approved.total()).isEqualByComparingTo("120.00");
        assertThat(approved.detalles()).hasSize(2);
        converges(approved.id(), "PAGADA", "VALIDADO");
        converges(create("TARJETA_RECHAZADA").id(), "CANCELADA", "FALLIDO");
    }

    @Test void immediateCancellationConvergesWithoutACancelledOrderAndApprovedPayment() {
        var order = create("YAPE");
        orders.estado(order.id(), Map.of("estado", "CANCELADA"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orderRepo.findById(order.id()).orElseThrow().getEstado()).isEqualTo("CANCELADA");
            assertThat(paymentRepo.findByIdOrden(order.id()).orElseThrow().getEstado())
                    .isIn("CANCELADO", "REEMBOLSADO");
        });
    }

    @Test void paidOrderIsRefundedAndDuplicateCommandsAndStaleApprovalDoNotChargeAgain() throws Exception {
        var order = create("YAPE");
        converges(order.id(), "PAGADA", "VALIDADO");
        var payment = paymentRepo.findByIdOrden(order.id()).orElseThrow();
        var approvalDate = payment.getFechaAprobacion();
        orders.estado(order.id(), Map.of("estado", "CANCELADA"));
        converges(order.id(), "CANCELADA", "REEMBOLSADO");
        long commands = orderOutbox.findAll().stream().filter(e -> e.getOrdenId().equals(order.id())).count();
        orders.estado(order.id(), Map.of("estado", "CANCELADA"));
        for (var e : orderOutbox.findAll()) {
            if (e.getOrdenId().equals(order.id())) {
                kafka.send("illari.orden.creada", order.id().toString(), e.getPayload()).get(10, TimeUnit.SECONDS);
            }
        }
        var stale = new PagoEventConsumer.PagoResultadoEvento("pago.validado", order.id(), order.idCliente(),
                order.total(), "VALIDADO", "pago-ms", Instant.now(), payment.getId(), 1L);
        kafka.send("illari.pago.resultado", order.id().toString(), mapper.writeValueAsString(stale)).get(10, TimeUnit.SECONDS);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(orderRepo.findById(order.id()).orElseThrow().getEstadoPago()).isEqualTo("REEMBOLSADO");
            var current = paymentRepo.findByIdOrden(order.id()).orElseThrow();
            assertThat(current.getEstado()).isEqualTo("REEMBOLSADO");
            assertThat(current.getId()).isEqualTo(payment.getId());
            assertThat(current.getFechaAprobacion()).isEqualTo(approvalDate);
            assertThat(paymentRepo.findAllByIdOrdenOrderByFechaCreacionAsc(order.id())).hasSize(1);
            assertThat(orderOutbox.findAll().stream().filter(e -> e.getOrdenId().equals(order.id())).count()).isEqualTo(commands);
        });
    }

    @Test void concurrentCancellationRequestsProduceOneCompensationCommand() throws Exception {
        var order = create("YAPE");
        converges(order.id(), "PAGADA", "VALIDADO");
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(() -> orders.estado(order.id(), Map.of("estado", "CANCELADA"))));
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        }
        converges(order.id(), "CANCELADA", "REEMBOLSADO");
        assertThat(orderOutbox.findAll().stream().filter(e -> e.getOrdenId().equals(order.id())
                && e.getPayload().contains("orden.cancelacion.solicitada")).count()).isEqualTo(1);
    }

    @Test void orderAndItsOutboxRollBackTogetherBeforeAnyPublication() {
        var id = new java.util.concurrent.atomic.AtomicReference<Long>();
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                ordersContext.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            id.set(create("YAPE").id());
            throw new IllegalStateException("simulated local transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(orderRepo.findById(id.get())).isEmpty();
        assertThat(orderOutbox.findAll().stream().filter(e -> e.getOrdenId().equals(id.get()))).isEmpty();
        assertThat(paymentRepo.findByIdOrden(id.get())).isEmpty();
    }

    @Test void paymentAndItsResultOutboxRollBackTogether() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                paymentsContext.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            paymentsContext.getBean(OrdenEventConsumer.class).onOrden(
                    "{\"tipoEvento\":\"orden.creada\",\"origen\":\"orden-legal-ms\",\"ordenId\":999999,"
                            + "\"idCliente\":\"saga-client\",\"total\":80.00,\"metodoPago\":\"YAPE\"}");
            throw new IllegalStateException("simulated local transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(paymentRepo.findByIdOrden(999999L)).isEmpty();
        assertThat(paymentsContext.getBean(PagoOutboxRepository.class).findByOrdenId(999999L)).isEmpty();
    }

    static OrdenLegalController.OrdenLegal create(String method) {
        Jwt jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject("saga-client").build();
        return orders.crear(new OrdenLegalController.NuevaOrden(method, List.of(
                new OrdenLegalController.DetalleNuevaOrden(7L, 2),
                new OrdenLegalController.DetalleNuevaOrden(8L, 1))), jwt);
    }

    static void converges(Long id, String orderState, String paymentState) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orderRepo.findById(id).orElseThrow().getEstado()).isEqualTo(orderState);
            assertThat(paymentRepo.findByIdOrden(id).orElseThrow().getEstado()).isEqualTo(paymentState);
        });
    }

    @Test void earliestOffsetsRecoverAnOrderPublishedBeforePaymentConsumerStarted() {
        converges(beforePaymentConsumer,"PAGADA","VALIDADO");
    }

    @Test void idempotencyAcrossTwoSpringInstancesCreatesOneOrderAndOnePayment() throws Exception {
        try(var second=start(OrdersConfig.class,"orders","--spring.jpa.hibernate.ddl-auto=none")) {
            var firstService=ordersContext.getBean(CompraIdempotenteService.class);
            var secondService=second.getBean(CompraIdempotenteService.class);
            var request=new OrdenLegalController.NuevaOrden("YAPE",List.of(new OrdenLegalController.DetalleNuevaOrden(7L,2),new OrdenLegalController.DetalleNuevaOrden(8L,1)));
            var jwt=Jwt.withTokenValue("test").header("alg","none").subject("shared-client").build();
            String key="two-instances-"+UUID.randomUUID();
            java.util.Set<Long> ids=new java.util.HashSet<>();
            try(var pool=Executors.newFixedThreadPool(8)) {
                var gate=new CountDownLatch(1); java.util.List<Future<Long>> tasks=new ArrayList<>();
                for(int i=0;i<16;i++){var svc=i%2==0?firstService:secondService;
                    tasks.add(pool.submit(()->{gate.await();return svc.crear(key,request,jwt).id();}));}
                gate.countDown();for(var f:tasks)ids.add(f.get(30,TimeUnit.SECONDS));
            }
            assertThat(ids).hasSize(1);Long id=ids.iterator().next();converges(id,"PAGADA","VALIDADO");
            assertThat(paymentRepo.findAllByIdOrdenOrderByFechaCreacionAsc(id)).hasSize(1);
            assertThat(new org.springframework.jdbc.core.JdbcTemplate(ordersContext.getBean(javax.sql.DataSource.class))
                    .queryForObject("select count(*) from orden_detalle where orden_id=?",Long.class,id)).isEqualTo(2);
        }
    }

    @Test void transientFailuresUseBackoffThenCommitExactlyOneEffect() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(ordersContext.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        Long id=tx.execute(s->{var order=create("YAPE");fallos.put(order.id(),new java.util.concurrent.atomic.AtomicInteger(2));return order.id();});
        converges(id,"PAGADA","VALIDADO");
        var calls=tiempos.get(id);assertThat(calls).hasSize(3);
        assertThat(TimeUnit.NANOSECONDS.toMillis(calls.get(1)-calls.get(0))).isGreaterThanOrEqualTo(180);
        assertThat(TimeUnit.NANOSECONDS.toMillis(calls.get(2)-calls.get(1))).isGreaterThanOrEqualTo(350);
        assertThat(paymentRepo.findAllByIdOrdenOrderByFechaCreacionAsc(id)).hasSize(1);
        assertThat(paymentsContext.getBean(io.micrometer.core.instrument.MeterRegistry.class)
                .get("illari.kafka.reintentados").tag("servicio","pago").counter().count()).isGreaterThanOrEqualTo(2);
    }

    @Test void exhaustedRetriesGoToDltAndControlledReplayRecoversTheOrder() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(ordersContext.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        Long id=tx.execute(s->{var order=create("YAPE");fallos.put(order.id(),new java.util.concurrent.atomic.AtomicInteger(10));return order.id();});
        var recovery=paymentsContext.getBean(com.illarilegal.pago.MensajeriaRecoveryService.class);
        var deadRepo=paymentsContext.getBean(com.illarilegal.pago.MensajeFallidoRepository.class);
        await().atMost(Duration.ofSeconds(25)).untilAsserted(()->assertThat(tiempos.get(id)).hasSize(4));
        await().atMost(Duration.ofSeconds(20)).until(()->deadRepo.findAll().stream().anyMatch(m->
                new org.springframework.jdbc.core.JdbcTemplate(paymentsContext.getBean(javax.sql.DataSource.class))
                        .queryForObject("select payload from pago_dead_letter where id=?",String.class,m.getId()).contains("\"ordenId\":"+id)));
        String deadId=new org.springframework.jdbc.core.JdbcTemplate(paymentsContext.getBean(javax.sql.DataSource.class))
                .queryForList("select id,payload from pago_dead_letter").stream()
                .filter(row->row.get("payload").toString().contains("\"ordenId\":"+id)).map(row->row.get("id").toString()).findFirst().orElseThrow();
        assertThat(paymentRepo.findByIdOrden(id)).isEmpty();fallos.get(id).set(0);
        recovery.solicitarReplay(deadId,null,"Dependencia recuperada en prueba","test-admin");
        recovery.publicarReplays();converges(id,"PAGADA","VALIDADO");
        assertThat(recovery.listar().stream().filter(v->v.id().equals(deadId)).findFirst().orElseThrow().operador()).isEqualTo("test-admin");
        assertThat(recovery.operaciones().stream().anyMatch(a->a.referencia().equals(deadId)&&a.operador().equals("test-admin"))).isTrue();
        var metrics=paymentsContext.getBean(io.micrometer.core.instrument.MeterRegistry.class);
        assertThat(metrics.get("illari.kafka.fallidos").tag("servicio","pago").counter().count()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.get("illari.kafka.reprocesados").tag("servicio","pago").counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test void poisonMessageIsCapturedOffsetsAdvanceAndCorrectedReplayIsConsumed() throws Exception {
        String poison="{invalid-"+UUID.randomUUID();
        var sent=kafka.send("illari.orden.creada","poison",poison).get(10,TimeUnit.SECONDS).getRecordMetadata();
        var recovery=paymentsContext.getBean(com.illarilegal.pago.MensajeriaRecoveryService.class);
        await().atMost(Duration.ofSeconds(20)).until(()->recovery.listar().stream().anyMatch(v->v.offset()==sent.offset()
                &&recovery.detalle(v.id()).payloadOriginal().equals(poison)));
        var dead=recovery.listar().stream().filter(v->v.offset()==sent.offset()
                &&recovery.detalle(v.id()).payloadOriginal().equals(poison)).findFirst().orElseThrow();
        var order=create("YAPE");converges(order.id(),"PAGADA","VALIDADO");
        try(var admin=org.apache.kafka.clients.admin.AdminClient.create(Map.of("bootstrap.servers",broker.getCluster().bootstrapServers()))) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(admin.listConsumerGroupOffsets("illari-pago-ms")
                    .partitionsToOffsetAndMetadata().get(5,TimeUnit.SECONDS).get(new org.apache.kafka.common.TopicPartition("illari.orden.creada",0)).offset()).isGreaterThan(sent.offset()));
        }
        String eventId=UUID.randomUUID().toString();
        String corrected="{\"eventId\":\""+eventId+"\",\"tipoEvento\":\"orden.creada\",\"origen\":\"orden-legal-ms\",\"ordenId\":"+order.id()
                +",\"idCliente\":\"saga-client\",\"total\":120.00,\"metodoPago\":\"YAPE\"}";
        recovery.solicitarReplay(dead.id(),corrected,"Corrección controlada de JSON","test-admin");recovery.publicarReplays();
        await().atMost(Duration.ofSeconds(20)).until(()->paymentsContext.getBean(com.illarilegal.pago.EventoRecibidoRepository.class).existsById(eventId));
        assertThat(paymentRepo.findAllByIdOrdenOrderByFechaCreacionAsc(order.id())).hasSize(1);
    }

    @Test void producerDisconnectionKeepsOutboxDurableAndRecoversWithoutDuplicatePayment() throws Exception {
        await().atMost(Duration.ofSeconds(10)).until(()->orderOutbox.contarPendientes()==0
                &&paymentsContext.getBean(PagoOutboxRepository.class).contarPendientes()==0);
        var factory=(org.springframework.kafka.core.DefaultKafkaProducerFactory<?,?>)ordersContext.getBean(org.springframework.kafka.core.ProducerFactory.class);
        int disconnectedPort;
        try(var socket=new java.net.ServerSocket(0)){disconnectedPort=socket.getLocalPort();}
        Long id;
        // A real producer connects to an unavailable endpoint; the broker/data remain intact.
        factory.updateConfigs(Map.of("bootstrap.servers","127.0.0.1:"+disconnectedPort));factory.reset();
        try {
            id=create("YAPE").id();
            await().atMost(Duration.ofSeconds(10)).until(()->orderOutbox.findAll().stream()
                    .anyMatch(e->e.getOrdenId().equals(id)&&e.getIntentos()>0));
            assertThat(orderRepo.findById(id).orElseThrow().getEstado()).isEqualTo("PENDIENTE_PAGO");
            assertThat(orderOutbox.findAll().stream().filter(e->e.getOrdenId().equals(id)).findFirst().orElseThrow().isPublicado()).isFalse();
        } finally {factory.updateConfigs(Map.of("bootstrap.servers",broker.getCluster().bootstrapServers()));factory.reset();}
        converges(id,"PAGADA","VALIDADO");
        assertThat(paymentRepo.findAllByIdOrdenOrderByFechaCreacionAsc(id)).hasSize(1);
    }

    @Test void reusedEventIdWithChangedContentIsRejectedWithoutOverwritingTheReceipt() throws Exception {
        var order=create("YAPE");converges(order.id(),"PAGADA","VALIDADO");
        String payload=orderOutbox.findAll().stream().filter(e->e.getOrdenId().equals(order.id())).findFirst().orElseThrow().getPayload();
        var tree=com.illarilegal.pago.EventEnvelope.parse(payload);
        String eventId=com.illarilegal.pago.EventEnvelope.id(tree);
        String originalHash=com.illarilegal.pago.EventEnvelope.hash(tree);
        tree.put("idCliente","foreign-client");
        var sent=kafka.send("illari.orden.creada",order.id().toString(),tree.toString()).get(10,TimeUnit.SECONDS).getRecordMetadata();
        var recovery=paymentsContext.getBean(com.illarilegal.pago.MensajeriaRecoveryService.class);
        await().atMost(Duration.ofSeconds(20)).until(()->recovery.listar().stream().anyMatch(v->v.offset()==sent.offset()
                &&recovery.detalle(v.id()).payloadOriginal().equals(tree.toString())));
        paymentsContext.getBean(com.illarilegal.pago.EventoRecibidoRepository.class).findById(eventId).orElseThrow().verificar(originalHash);
        assertThat(paymentRepo.findByIdOrden(order.id()).orElseThrow().getIdCliente()).isEqualTo("saga-client");
    }

    @Test void deadLetterIdentitySurvivesTopicRecreationWithoutLosingHistory() {
        var recovery=paymentsContext.getBean(com.illarilegal.pago.MensajeriaRecoveryService.class);
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(paymentsContext.getBean(javax.sql.DataSource.class));
        String payload="invalid-"+UUID.randomUUID();
        var first=deadLetterRecord(payload,100);
        recovery.capturar(first);recovery.capturar(first);
        assertThat(jdbc.queryForObject("select count(*) from pago_dead_letter where payload=?",Long.class,payload)).isEqualTo(1);
        recovery.capturar(deadLetterRecord(payload,101));
        assertThat(jdbc.queryForObject("select count(*) from pago_dead_letter where payload=?",Long.class,payload)).isEqualTo(2);
    }
    private static org.apache.kafka.clients.consumer.ConsumerRecord<String,String> deadLetterRecord(String payload,long time) {
        var record=new org.apache.kafka.clients.consumer.ConsumerRecord<String,String>("illari.orden.creada.DLT",0,0,"key",payload);
        record.headers().add(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET,java.nio.ByteBuffer.allocate(8).putLong(0).array());
        record.headers().add(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_PARTITION,java.nio.ByteBuffer.allocate(4).putInt(0).array());
        record.headers().add(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TIMESTAMP,java.nio.ByteBuffer.allocate(8).putLong(time).array());
        return record;
    }
}
