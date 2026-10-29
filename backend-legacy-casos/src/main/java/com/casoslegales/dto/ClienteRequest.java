package com.casoslegales.dto;

import jakarta.validation.constraints.*;

public record ClienteRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,
        
        @NotBlank(message = "El teléfono es obligatorio")
        String telefono,
        
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe ser válido")
        String email,
        
        String direccion
) {}
