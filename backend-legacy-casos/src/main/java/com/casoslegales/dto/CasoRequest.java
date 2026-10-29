package com.casoslegales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CasoRequest(
        String nombreCaso,
        Long clienteId,
        Long abogadoId,
        String estado,
        LocalDate fechaApertura,
        String descripcion,
        BigDecimal tarifa,
        LocalDate fechaAudiencia
) {}
