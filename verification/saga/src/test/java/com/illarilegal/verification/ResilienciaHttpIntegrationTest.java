package com.illarilegal.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illarilegal.ordenlegal.CatalogoResilienteService;
import com.illarilegal.ordenlegal.ServicioLegalFeignClient;
import com.illarilegal.serviciolegal.*;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real catalogue controller/repository, HTTP and Feign. Only private in-memory H2.
 * Separate Spring contexts in one test JVM; does not prove Docker/Eureka deployment.
 */
class ResilienciaHttpIntegrationTest {
    static final AtomicBoolean slow = new AtomicBoolean();
    static final AtomicInteger attempts = new AtomicInteger();
    static String base;

    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @EntityScan(basePackageClasses=ServicioLegalEntity.class)
    @EnableJpaRepositories(basePackageClasses=ServicioLegalRepository.class)
    @Import({ServicioLegalController.class, DistributedSecurityConfig.class, JwtValidationConfig.class})
    static class Catalogue {
        @Bean OncePerRequestFilter controlledLatency() {
            return new OncePerRequestFilter() {
                @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,
                        jakarta.servlet.http.HttpServletResponse res, jakarta.servlet.FilterChain chain)
                        throws java.io.IOException, jakarta.servlet.ServletException {
                    if(slow.get()) try { Thread.sleep(500); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                    chain.doFilter(req,res);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import(CatalogoResilienteService.class)
    static class Consumer {
        @Bean ServicioLegalFeignClient httpClient(ObjectMapper mapper) {
            var delegate=Feign.builder().contract(new SpringMvcContract())
                .options(new Request.Options(200,TimeUnit.MILLISECONDS,200,TimeUnit.MILLISECONDS,true))
                .retryer(Retryer.NEVER_RETRY)
                .decoder((response,type)->mapper.readValue(response.body().asInputStream(),mapper.constructType(type)))
                .target(ServicioLegalFeignClient.class,base);
            return id->{attempts.incrementAndGet();return delegate.obtener(id);};
        }
    }

    static ConfigurableApplicationContext catalogue(int port,String database) {
        return new SpringApplicationBuilder(Catalogue.class).web(WebApplicationType.SERVLET).run(
            "--server.address=127.0.0.1","--server.port="+port,
            "--spring.application.name=s12-http-catalogue","--spring.main.banner-mode=off","--logging.level.root=WARN",
            "--spring.cloud.config.enabled=false","--spring.cloud.config.import-check.enabled=false","--eureka.client.enabled=false",
            "--spring.datasource.url=jdbc:h2:mem:"+database+";DB_CLOSE_DELAY=-1",
            "--spring.datasource.username=sa","--spring.datasource.password=","--spring.jpa.hibernate.ddl-auto=update",
            "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test/illari",
            "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/unused");
    }

    @Test void realHttpTimeoutRetriesFallbackServiceStopAndRecovery() {
        ConfigurableApplicationContext catalog=null,consumer=null;
        String database="s12_http_"+java.util.UUID.randomUUID().toString().replace("-","");
        try {
            catalog=catalogue(0,database);
            int port=((ServletWebServerApplicationContext)catalog).getWebServer().getPort();
            base="http://127.0.0.1:"+port;
            long id=catalog.getBean(ServicioLegalRepository.class).save(new ServicioLegalEntity(
                "Servicio de prueba S12","Fixture efímero",new BigDecimal("20.00"),"CIVIL","ACTIVO")).getId();
            consumer=new SpringApplicationBuilder(Consumer.class).web(WebApplicationType.NONE).run(
                "--spring.main.banner-mode=off","--logging.level.root=WARN","--spring.cloud.config.enabled=false",
                "--spring.cloud.config.import-check.enabled=false","--eureka.client.enabled=false",
                "--spring.datasource.url=jdbc:h2:mem:"+database+"_consumer","--spring.datasource.username=sa",
                "--spring.datasource.password=","--resilience4j.retry.instances.servicioLegal.maxAttempts=2",
                "--resilience4j.retry.instances.servicioLegal.waitDuration=30ms",
                "--resilience4j.circuitbreaker.instances.servicioLegal.minimumNumberOfCalls=3",
                "--resilience4j.circuitbreaker.instances.servicioLegal.slidingWindowSize=5",
                "--resilience4j.circuitbreaker.instances.servicioLegal.waitDurationInOpenState=1s");
            var service=consumer.getBean(CatalogoResilienteService.class);
            var breaker=consumer.getBean(CircuitBreakerRegistry.class).circuitBreaker("servicioLegal");
            assertThat(service.obtener(id).id()).isEqualTo(id);
            breaker.reset(); attempts.set(0); slow.set(true);
            long start=System.nanoTime();
            unavailable(service,id);
            long elapsed=Duration.ofNanos(System.nanoTime()-start).toMillis();
            assertThat(attempts.get()).isEqualTo(2);
            assertThat(elapsed).isBetween(350L,3000L);
            System.out.println("S12_HTTP timeout_ms="+elapsed+" attempts="+attempts.get()+" fallback=503");
            slow.set(false);
            catalog.close(); catalog=null; breaker.reset(); attempts.set(0);
            unavailable(service,id); unavailable(service,id);
            assertThat(breaker.getState().name()).isEqualTo("OPEN");
            int before=attempts.get(); unavailable(service,id);
            assertThat(attempts.get()).isEqualTo(before);
            System.out.println("S12_HTTP catalogue_stopped=true circuit=OPEN no_extra_http=true");
            catalog=catalogue(port,database);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(()->{
                assertThat(service.obtener(id).precio()).isEqualByComparingTo("20.00");
                assertThat(breaker.getState().name()).isEqualTo("CLOSED");
            });
            System.out.println("S12_HTTP catalogue_restarted=true circuit=CLOSED recovered=true");
        } finally {
            slow.set(false);
            if(consumer!=null)consumer.close();
            if(catalog!=null)catalog.close();
        }
    }

    static void unavailable(CatalogoResilienteService service,long id) {
        assertThatThrownBy(()->service.obtener(id)).isInstanceOfSatisfying(ResponseStatusException.class,
            error->assertThat(error.getStatusCode().value()).isEqualTo(503));
    }

    @Test void publicValidationErrorsKeep400WithoutOpeningProtectedRequests() throws Exception {
        String database="s12_validation_"+java.util.UUID.randomUUID().toString().replace("-","");
        try(var catalog=catalogue(0,database)) {
            int port=((ServletWebServerApplicationContext)catalog).getWebServer().getPort();
            var http=java.net.http.HttpClient.newHttpClient();
            var invalid=java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://127.0.0.1:"+port+"/api/v1/servicios/no-numerico")).GET().build();
            assertThat(http.send(invalid,java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(400);
            var protectedWrite=java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://127.0.0.1:"+port+"/api/v1/servicios"))
                .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build();
            assertThat(http.send(protectedWrite,java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            var errorPath=java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://127.0.0.1:"+port+"/error")).GET().build();
            assertThat(http.send(errorPath,java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        }
    }
}
