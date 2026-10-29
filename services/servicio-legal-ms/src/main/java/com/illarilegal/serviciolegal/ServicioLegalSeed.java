package com.illarilegal.serviciolegal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ServicioLegalSeed implements CommandLineRunner {
    private final ServicioLegalRepository servicios;

    public ServicioLegalSeed(ServicioLegalRepository servicios) {
        this.servicios = servicios;
    }

    @Override
    public void run(String... args) {
        List<ServicioLegalEntity> catalogoInicial = List.of(
                new ServicioLegalEntity("Consulta jurídica", "Consulta inicial", new BigDecimal("80.00"), "Asesorías", "ACTIVO"),
                new ServicioLegalEntity("Asesoría civil", "Asesoría especializada en contratos, propiedad, herencias y obligaciones.", new BigDecimal("150.00"), "Asesorías", "ACTIVO"),
                new ServicioLegalEntity("Revisión de documentos", "Revisión legal de contratos y documentos.", new BigDecimal("120.00"), "Asesorías", "ACTIVO"),
                new ServicioLegalEntity("Asesoría Penal", "Defensa y orientación en procesos penales.", new BigDecimal("70.00"), "Asesorías", "ACTIVO"),
                new ServicioLegalEntity("Código Civil del Perú", "Texto actualizado con últimas modificaciones.", new BigDecimal("35.00"), "Códigos y leyes", "ACTIVO"),
                new ServicioLegalEntity("Código Penal del Perú", "Texto actualizado con últimas modificaciones.", new BigDecimal("40.00"), "Códigos y leyes", "ACTIVO"),
                new ServicioLegalEntity("Código Procesal Civil", "Texto actualizado con jurisprudencia.", new BigDecimal("38.00"), "Códigos y leyes", "ACTIVO"),
                new ServicioLegalEntity("Modelo de demanda civil", "Formato editable con guía de uso e instructivo.", new BigDecimal("25.00"), "Modelos de escritos", "ACTIVO"),
                new ServicioLegalEntity("Modelo de contrato de arrendamiento", "Formato editable y personalizable con cláusulas útiles.", new BigDecimal("28.00"), "Contratos", "ACTIVO"),
                new ServicioLegalEntity("Manual de Derecho Laboral", "Análisis práctico con casos reales.", new BigDecimal("60.00"), "Libros jurídicos", "ACTIVO"),
                new ServicioLegalEntity("Curso de Derecho Administrativo", "Clases grabadas y materiales de estudio. Acceso por 6 meses.", new BigDecimal("120.00"), "Capacitaciones", "ACTIVO"),
                new ServicioLegalEntity("Modelo de apelación penal", "Formato editable con jurisprudencia.", new BigDecimal("25.00"), "Modelos de escritos", "ACTIVO"),
                new ServicioLegalEntity("Constitución Política del Perú", "Texto actualizado de la Constitución Política del Perú.", new BigDecimal("15.00"), "Códigos y leyes", "ACTIVO"),
                new ServicioLegalEntity("Colección de sentencias del Tribunal Constitucional", "Selección de jurisprudencia relevante.", new BigDecimal("45.00"), "Jurisprudencia", "ACTIVO"));

        catalogoInicial.stream()
                .filter(servicio -> !servicios.existsByNombreIgnoreCase(servicio.getNombre()))
                .forEach(servicios::save);
    }
}
