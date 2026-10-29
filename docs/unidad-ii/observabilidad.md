# Observabilidad

## Prometheus

La verificación registró:

| Métrica | Series |
|---|---:|
| up | 5 |
| http_server_requests_seconds_count | 47 |
| resilience4j_circuitbreaker_state | 6 |
| resilience4j_retry_calls_total | 4 |
| jvm_memory_used_bytes | 40 |
| process_uptime_seconds | 5 |

Los cinco targets consultados estaban UP.

## Grafana

La API de salud respondió correctamente.

## Evidencia adicional necesaria

Verificar y capturar un dashboard que muestre
métricas de al menos dos microservicios.
