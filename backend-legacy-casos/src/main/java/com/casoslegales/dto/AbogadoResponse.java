package com.casoslegales.dto;

public record AbogadoResponse(
        Long id,
        String nombre,
        String telefono,
        String email,
        String especialidad,
        String direccion,
        Long userId
) {}
