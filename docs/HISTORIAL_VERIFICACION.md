# Historial de verificación consolidado

| Etapa | Resultado documentado | Alcance |
|---|---|---|
| Fase1 |58 casos distintos |53 Java/SAGA y5 Angular; correcciones críticas |
| Fase2 |98 casos distintos |84 Java/SAGA y14 Angular; idempotencia/carrito/recuperación |
| Fase3 |119 casos y268 comprobaciones integradas | Infraestructura y seguridad real |
| Fase4 |138 casos;175 comprobaciones integradas |111 Java/SAGA y27 Angular, observabilidad |
| Fase5 A |37 Angular y30 observaciones favorables de navegador | Dos fallos históricos corregidos; compra53 |
| Fase5 B |64 pruebas de Órdenes; etapas251/31/17/151/33/34 aprobadas | No suma de casos únicos; incluye preparación/login y verificaciones repetidas |
| Fase5 C |51 Angular y build | Nueva ejecución desde entrega; otros resultados se reutilizan si sus fuentes no cambiaron |

Las cifras por fase no se suman. Pruebas JVM/embebidas, HTTP, Docker, SQL y navegador se mantienen separadas. Los informes originales y capturas sin transformar permanecen en la copia de desarrollo; el export contiene evidencia seleccionada/sanitizada con hashes de procedencia. Los pendientes antiguos se contextualizan en el informe final y no se presentan como fallos actuales automáticamente.
