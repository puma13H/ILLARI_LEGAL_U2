package com.illarilegal.ordenlegal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/ordenes/resiliencia")
public class ResilienciaController {
 private final CatalogoResilienteService catalogo;
 public ResilienciaController(CatalogoResilienteService catalogo){this.catalogo=catalogo;}
 @GetMapping("/servicio/{id}")
 // Use the purchase path's Feign, discovery and error classification; never poison its shared breaker.
 public OrdenLegalController.ServicioLegal consultar(@PathVariable Long id){return catalogo.obtener(id);}
}
