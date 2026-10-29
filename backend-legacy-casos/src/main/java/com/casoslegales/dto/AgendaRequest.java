package com.casoslegales.dto;

import java.time.LocalDateTime;

public record AgendaRequest(
        String titulo,
        String descripcion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin
) {}
