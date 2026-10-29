# Solución de errores comunes

| Síntoma | Comprobar | Corrección segura |
|---|---|---|
| Login no abre Keycloak | Estado Docker, puerto/issuer público | Arrancar contenedores existentes; no cambiar credenciales |
| Token sin sub o audiencia | Scope basic y mapper illari-api del realm | Revisar configuración del realm; no reimportar sobrescribiendo usuarios |
|401| Firma/expiración/issuer/audiencia | Nuevo login válido; no desactivar el resource server |
|403 en caso o agenda | Rol real y vínculo local, propietario | Vincular/revisar IDs autorizados; agenda general solo ADMIN |
|404 en orden ajena | Política de ocultamiento | No revelar existencia ni ampliar acceso para «arreglar»404 |
|502 o spinner | DNS nginx/Gateway y Eureka | Resolver dinámicamente y esperar rutas; no confiar solo en la página estática |
|503 comprando | Catálogo, timeout/circuito | Restaurar catálogo y esperar llamadas HALF_OPEN; mantener la misma clave si resultado incierto |
|409 con Idempotency-Key | Solicitud distinta con la misma clave | No cambiar una compra incierta; consultar resultado original |
|CANCELACION_PENDIENTE| Broker, comandos outbox y resultado de compensación | Recuperación ADMIN auditada; no editar estados con SQL |
|Outbox agotado| Publicaciones/errores, broker ya recuperado | Motivo explícito y reintento ADMIN; no borrar la fila |
|DLT pendiente| Contrato, coordenadas y causa | Revisar/corregir fixture y replay ADMIN; confirmar inbox y ausencia de duplicados |
|Panel vacío| Ventana, consulta y datasource | Generar actividad real; un ALERTS vacío sano no es fallo, un panel de latencia sin muestras no se presume aprobado |
|Loki sin logs| Alloy, volúmenes de observación y posiciones | Revisar solo el proyecto; nunca montar el socket Docker para saltar el diagnóstico |
|ddl-auto=validate falla| Base/esquema/migraciones | Inspección+respaldo restaurado antes de autorización; no cambiar a create-drop |
|Puerto ocupado| Compose port y otros proyectos | Usar puertos libres y alinear URLs; no detener otro proyecto |
|Faltan JAR/dist en ZIP| Son generados excluidos | Ejecutar BUILD-S12 con pruebas antes de construir imágenes |
|Mockito/loopback falla en sandbox| Restricción del entorno de ejecución | Repetir con permisos de laboratorio; no omitir pruebas ni instrumentación |
|Sesión expira/refresh rechazado| Disponibilidad Keycloak y sesión remota | Volver al login; tokens no se guardan en disco para mantenerlos vivos |

El laboratorio se limita a loopback. Para reanudar servicios existentes usar `docker compose ... start --wait`; para detener conservando información usar `stop`. No usar borrado de volúmenes como solución.
