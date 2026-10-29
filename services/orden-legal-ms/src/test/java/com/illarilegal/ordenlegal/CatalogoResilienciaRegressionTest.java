package com.illarilegal.ordenlegal;

import feign.*;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = CatalogoResilienciaRegressionTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false",
                "resilience4j.retry.instances.servicioLegal.waitDuration=1ms"})
@org.springframework.test.context.ActiveProfiles("resiliencia-regression")
class CatalogoResilienciaRegressionTest {
    @Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Profile("resiliencia-regression")
    @EnableAutoConfiguration
    @Import({CatalogoResilienteService.class, ResilienciaController.class})
    static class Config {}

    @Autowired CatalogoResilienteService service;
    @Autowired ResilienciaController diagnostic;
    @Autowired CircuitBreakerRegistry breakers;
    @MockBean ServicioLegalFeignClient feign;

    @BeforeEach void resetState() {
        breakers.circuitBreaker("servicioLegal").reset();
        reset(feign);
    }

    @Test void missingIdsAreNotRetriedAndDoNotOpenTheCircuit() {
        when(feign.obtener(999L)).thenThrow(httpError(404));
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> service.obtener(999L)).isInstanceOfSatisfying(
                    ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verify(feign, times(6)).obtener(999L);
        assertThat(breakers.circuitBreaker("servicioLegal").getState().name()).isEqualTo("CLOSED");
        assertThat(breakers.circuitBreaker("servicioLegal").getMetrics().getNumberOfFailedCalls()).isZero();
        when(feign.obtener(1L)).thenReturn(new OrdenLegalController.ServicioLegal(
                1L, "Legal", "", java.math.BigDecimal.TEN, "LEGAL", "ACTIVO"));
        assertThat(service.obtener(1L).id()).isEqualTo(1L);
    }

    @Test void diagnosticRouteAlsoExcludesBusinessErrorsFromTheSharedCircuit() {
        when(feign.obtener(999L)).thenThrow(httpError(404));
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> diagnostic.consultar(999L)).isInstanceOfSatisfying(
                    ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verify(feign, times(6)).obtener(999L);
        assertThat(breakers.circuitBreaker("servicioLegal").getState().name()).isEqualTo("CLOSED");
        assertThat(breakers.circuitBreaker("servicioLegal").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test void diagnosticRouteUsesTheSameAvailabilityPolicyAsPurchases() {
        when(feign.obtener(1L)).thenThrow(httpError(503));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> diagnostic.consultar(1L)).isInstanceOfSatisfying(
                    ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        }
        assertThat(breakers.circuitBreaker("servicioLegal").getState().name()).isEqualTo("OPEN");
    }

    @Test void unavailableCatalogIsRetriedAndOpensTheCircuitWithControlled503() {
        when(feign.obtener(1L)).thenThrow(httpError(503));
        assert503();
        verify(feign, times(2)).obtener(1L);
        assert503();
        assertThat(breakers.circuitBreaker("servicioLegal").getState().name()).isEqualTo("OPEN");
        clearInvocations(feign);
        assert503();
        verifyNoInteractions(feign);
    }

    private void assert503() {
        assertThatThrownBy(() -> service.obtener(1L)).isInstanceOfSatisfying(
                ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test void networkTimeoutIsRetriedAndRecordedAsAnAvailabilityFailure() {
        Request request = Request.create(Request.HttpMethod.GET, "http://catalogo/test",
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        when(feign.obtener(1L)).thenThrow(new RetryableException(-1, "timeout", Request.HttpMethod.GET,
                new java.net.SocketTimeoutException("test timeout"), (Long) null, request));
        assert503();
        verify(feign, times(2)).obtener(1L);
        assertThat(breakers.circuitBreaker("servicioLegal").getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    private static FeignException httpError(int status) {
        return FeignException.errorStatus("catalogo", Response.builder().status(status).reason("test")
                .headers(Map.of()).request(Request.create(Request.HttpMethod.GET, "http://catalogo/test",
                        Map.of(), new byte[0], StandardCharsets.UTF_8, null)).build());
    }
}
