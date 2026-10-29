package com.illarilegal.ordenlegal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Eureka resolves the logical service name; no fixed container address. */
@FeignClient(name = "servicio-legal-ms")
public interface ServicioLegalFeignClient {
    @GetMapping("/api/v1/servicios/{id}")
    OrdenLegalController.ServicioLegal obtener(@PathVariable("id") Long id);
}
