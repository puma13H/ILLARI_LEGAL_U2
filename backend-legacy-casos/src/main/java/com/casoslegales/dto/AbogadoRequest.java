package com.casoslegales.dto;

public record AbogadoRequest(
        String nombre,
        String telefono,
        String email,
        String especialidad,
        String direccion
) {}
