package com.casoslegales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CasoResponse(
        Long id,
        String nombreCaso,
        Long clienteId,
        String clienteNombre,
        Long abogadoId,
        String abogadoNombre,
        String estado,
        LocalDate fechaApertura,
        String descripcion,
        BigDecimal tarifa,
        LocalDate fechaAudiencia
) {}
