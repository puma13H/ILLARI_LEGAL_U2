package com.casoslegales.dto;

public record MensajeResponse(
        Long id,
        Long casoId,
        Long userId,
        String userName,
        String contenido
) {}
