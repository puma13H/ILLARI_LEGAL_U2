# Matriz de evidencia S06–S12

| Sesión | Implementado y probado | Evidencia | Pendiente / no acreditado |
|---|---|---|---|
| S06 | Feign/Eureka, timeout, Retry, Circuit Breaker y fallback; negocio400 excluido; caída real y recuperación | evidencias/S06; B diagnóstico151/0; regresión y package64/0 | No prueba HA ni carga |
| S07/S07b | Keycloak, S256, firma/claims JWT;200/401/403 y recursos ajenos en Gateway y5 backends | evidencias/S07; etapa seguridad251/0 y A navegador | ADMIN de aplicación no equivale a administrador del IdP |
| S08 | Kafka real, inbox/outbox, ACK explícito, offsets, ambas DLT y replay | evidencias/S08-S09; offsets y logs | Backoff transitorio de consumidor reutiliza evidencia JVM reciente; no otra inyección Docker en C |
| S09 | Aprobación/rechazo, cancelación/reembolso, duplicados/tardíos,409 y concurrencia entre2 JVM | B31/0 y17/0; validación de pagos33/0; cierre34/0 | Simulación monetaria; no ACID global |
| S10 | Métricas/readiness,22 consultas de paneles, logs/traza y6 alertas con fallos reales | evidencias/S10; panel20 sano vacío con disparos guardados | Las consultas API no son capturas de Grafana; sin Tempo/Alertmanager |
| S11 | Angular por Gateway, guards, carrito N y compra; mensajes/agenda integrados en C | evidencias/S11-S12; Angular51/0 y build | Sin CRUD global de usuarios ni planificador avanzado |
| S12 | Integración técnica, guion, matriz y documentación local navegable | Esta carpeta, manifiestos y guion | Falta exposición y defensa individual, publicación autorizada y ensayo personal |

## Rúbrica oficial

| Criterio resumido | Peso publicado | Dictamen técnico |
|---|---:|---|
| Resiliencia |12%| Evidencia real de caída/respuesta controlada/recuperación |
| Seguridad distribuida |16%| Evidencia en Gateway y servicios propios |
| Mensajería |16%| Publicación/consumo y desacople ante interrupción |
| Consistencia |16%| Compensación e idempotencia probadas |
| Observabilidad |12%| Diagnóstico con métricas/logs/health correlacionados |
| Frontend |8%| Flujo principal completo; límites de módulos indicados |
| Sustentación |20%| Evaluación humana pendiente |

Los pesos no son porcentajes de cumplimiento ni calificaciones asignadas. No se inventa una nota. Fuentes: [producto U2](https://262dist.github.io/pagatu/proyecto-sello/u2-producto/) y [evaluación S12](https://262dist.github.io/pagatu/sesiones/S12_Evaluacion_Unidad_2/), consultadas con HTTP200 el28/10/2026. S12 menciona siete subaspectos de defensa; la tabla del producto enumera seis. Se registra la diferencia, sin crear un séptimo requisito arbitrario.
