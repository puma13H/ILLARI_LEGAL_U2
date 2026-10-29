package com.casoslegales.dto;

import org.springframework.core.io.Resource;

public record DocumentoResource(
        Resource resource,
        String nombre,
        String extension
) {}
