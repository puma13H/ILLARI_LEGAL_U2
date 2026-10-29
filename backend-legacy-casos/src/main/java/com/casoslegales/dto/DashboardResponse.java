package com.casoslegales.dto;

public record DashboardResponse(
        long totalCasos,
        long totalClientes,
        long totalAbogados,
        long casosActivos
) {}
