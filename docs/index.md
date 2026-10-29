# ILLARI LEGAL

## Sistema distribuido para servicios jurídicos

Proyecto académico de la Unidad II de Desarrollo de Aplicaciones Distribuidas.

### Componentes

- Frontend Angular
- API Gateway
- Keycloak y JWT
- Microservicios Spring Boot
- PostgreSQL
- Apache Kafka
- Saga, Inbox y Outbox
- Resilience4j
- Prometheus y Grafana

## Producto de Unidad II

Consulta el documento [Producto U2](unidad-ii/producto.md).

## Evidencias verificadas

Durante las pruebas funcionales se comprobaron:

- Tres órdenes pagadas.
- Una orden cancelada por pago rechazado.
- Persistencia de órdenes y pagos en bases independientes.
- Eventos de pago publicados mediante Outbox.
- Registro de eventos procesados mediante Inbox.
- Métricas de aplicaciones disponibles en Prometheus.

Las pruebas de caída real y de idempotencia deben documentarse
por separado cuando se ejecuten.
