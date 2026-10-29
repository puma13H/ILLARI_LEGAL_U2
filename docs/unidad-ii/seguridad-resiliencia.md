# Seguridad y resiliencia

## Seguridad

El sistema integra Keycloak y JWT.

Se comprobó que el Gateway rechaza una solicitud
sin autenticación hacia el recurso protegido de órdenes.

## Resiliencia

Se identificó configuración de Resilience4j.

Prometheus dispone de métricas de Circuit Breaker y Retry.

## Pruebas pendientes de documentar

- Apertura real de Circuit Breaker ante una falla controlada.
- Recuperación del servicio.
- Comportamiento del fallback.

No debe declararse una prueba aprobada hasta registrar
su ejecución y resultado.
