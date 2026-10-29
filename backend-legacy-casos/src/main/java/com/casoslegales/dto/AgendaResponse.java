package com.casoslegales.dto;

import java.time.LocalDateTime;

public record AgendaResponse(
        Long id,
        String titulo,
        String descripcion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin
) {}
