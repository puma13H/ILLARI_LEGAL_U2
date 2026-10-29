# ILLARI LEGAL — Entrega Unidad 2

Gestión de servicios jurídicos con Angular, Spring Boot, PostgreSQL, Keycloak, Kafka y observabilidad. Esta carpeta es la versión independiente de entrega; la copia de desarrollo y las evidencias originales permanecen intactas.

**Estado:** capacidades técnicas S06–S11 verificadas en laboratorio. La sustentación individual y publicación académica no se declaran aprobadas. Usuarios no incluye un CRUD global; las identidades se administran en Keycloak. Pagos y reembolsos son simulados.

## Comenzar

1. Leer [instalación desde cero](GUIA_INSTALACION.md).
2. Crear el archivo privado de variables fuera del proyecto usando `.env.s12.example`; no reutilizar esa plantilla para cambiar credenciales de un volumen existente.
3. Ejecutar `BUILD-S12.ps1`: Maven package con pruebas y Angular test/build.
4. Iniciar el proyecto aislado siguiendo la guía; activar observabilidad con el mismo nombre de proyecto y overlay.
5. Crear las cuentas y vínculos jurídicos propios sin incluir sus credenciales en esta carpeta.

## Documentos

- [Producto U2 adaptado](u2-producto.md)
- [Informe final](INFORME_FINAL_UNIDAD_2.md)
- [Resultados Fase5](FASE5_RESULTADOS.md)
- [Matriz S12](MATRIZ_CUMPLIMIENTO_S12.md)
- [Guion y preguntas](GUIA_DE_SUSTENTACION.md)
- [Checklist](CHECKLIST_ENTREGA_FINAL.md)
- [Arquitectura y contextos](docs/ARQUITECTURA.md)
- [Carrito N:M](docs/CARRITO_RELACIONAL.md)
- [Observabilidad](docs/OBSERVABILIDAD.md)
- [Errores comunes](SOLUCION_ERRORES.md)
- [Verificadores](verification/README.md)
- [Índice de evidencias](evidencias/README.md)
- [Inventario y decisiones](docs/ORGANIZACION.md)

La documentación HTML navegable está en `docs/sitio/index.html`. Puede abrirse localmente o servirse en loopback: `python -m http.server 8765 --bind 127.0.0.1 --directory .`, luego abrir `http://127.0.0.1:8765/docs/sitio/`. Esto no publica el proyecto en Internet. Regenerar con `python verification/entrega/generar_documentacion.py`.

## Estructura

| Carpeta | Responsabilidad |
|---|---|
| services | Clientes, Servicios Legales, Órdenes, Pagos |
| backend-legacy-casos | Casos, abogados, clientes jurídicos, documentos, agenda y mensajes |
| infra | Config Server, Eureka, Gateway, Keycloak y Dockerfiles |
| database | Esquema y migraciones aditivas; bootstrap solo para base vacía |
| obs | Prometheus, Grafana, Loki, Alloy y Blackbox |
| verification | Pruebas SAGA, PostgreSQL y verificadores HTTP/Docker |
| frontend | Angular y nginx del cliente |
| docs / evidencias | Documentación local y evidencia sanitizada |

No se distribuyen node_modules, target, dist, cachés, archivos privados de entorno, respaldos, documentos jurídicos ni contraseñas/tokens de pruebas. Las cadenas sintéticas de pruebas unitarias no son credenciales operativas. El paquete no contiene las cuentas del laboratorio ni sus bases.
