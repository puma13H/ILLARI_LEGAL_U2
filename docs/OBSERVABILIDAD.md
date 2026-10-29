# Observabilidad para demostrar S10

Componer `docker-compose.yml` y `obs/compose.s12.yml` con perfiles ui/obs y el mismo proyecto. Prometheus hace scrape de Gateway, Clientes, Catálogo, Órdenes, Pagos y Jurídico, más6 sondas HTTP readiness mediante Blackbox. Grafana autenticado consulta Prometheus/Loki. Alloy lee solo archivos permitidos en volúmenes de logs de solo lectura, sin socket Docker.

Dashboard UID `illari-s12-overview`,22 paneles. Consultas y resultados reales en evidencias/S10. Estados de negocio e inbox son gauges durables; counters operativos reinician con JVM. El panel DLT cuenta registros sin resolver, no lag físico del topic.

Alertas académicas: servicio no disponible, proporción5xx, circuito abierto, outbox agotado/antiguo y DLT pendiente. Se guardaron disparos y posterior recuperación. Un panel20 sin ALERTS en estado sano es esperado. No existen Alertmanager ni notificaciones externas.

En Explore/Loki:

```logql
{job="illari-observations"} | json | traceId="IDENTIFICADOR_TECNICO"
```

TraceId no es orderId ni eventId. Filtrar un eventId permite ver publicador/consumidor. El esquema de logs excluye cuerpos, JWT, passwords, emails y documentos. Los contadores no llevan identificadores personales como etiquetas.

Las evidencias B son consultas API autenticadas, métricas y logs; no se presentan como nuevas capturas visuales del dashboard. Abrir Grafana y ensayar personalmente su navegación antes de sustentar. Su puerto se consulta con Compose port; no se publican Loki/Prometheus/Alloy directamente.
