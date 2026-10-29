package com.illarilegal.ordenlegal;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resiliencia aplicada al flujo de negocio, no solo al endpoint demostrativo. */
@Service
public class CatalogoResilienteService {
    private static final Logger log = LoggerFactory.getLogger(CatalogoResilienteService.class);
    private final ServicioLegalFeignClient catalogo;

    public CatalogoResilienteService(ServicioLegalFeignClient catalogo) { this.catalogo = catalogo; }

    @Retry(name = "servicioLegal")
    @CircuitBreaker(name = "servicioLegal", fallbackMethod = "fallback")
    public OrdenLegalController.ServicioLegal obtener(Long id) {
        try {
            return catalogo.obtener(id);
        } catch (FeignException error) {
            if (error.status() >= 400 && error.status() < 500 && error.status() != 408 && error.status() != 429) {
                throw new CatalogoBusinessException(
                        error.status() == 404 ? HttpStatus.BAD_REQUEST : org.springframework.http.HttpStatusCode.valueOf(error.status()),
                        error.status() == 404 ? "El servicio " + id + " no existe." : "El catálogo rechazó la consulta.");
            }
            throw error;
        }
    }

    public OrdenLegalController.ServicioLegal fallback(Long id, Throwable error) {
        if (error instanceof ResponseStatusException expected) throw expected;
        log.warn("catalogo_fallback servicioId={} causa={}", id, error.getClass().getSimpleName());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Catálogo no disponible temporalmente. La orden no fue creada.");
    }
}
