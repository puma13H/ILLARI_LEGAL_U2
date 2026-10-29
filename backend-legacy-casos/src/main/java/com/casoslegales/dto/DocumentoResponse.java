package com.casoslegales.dto;

public record DocumentoResponse(
        Long id,
        String nombre,
        String tipoArchivo,
        Long tamanoArchivo,
        Long casoId
) {}
