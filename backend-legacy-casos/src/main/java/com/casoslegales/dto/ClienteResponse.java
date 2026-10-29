package com.casoslegales.dto;

public record ClienteResponse(
        Long id,
        String nombre,
        String telefono,
        String email,
        String direccion,
        Long userId
) {}
