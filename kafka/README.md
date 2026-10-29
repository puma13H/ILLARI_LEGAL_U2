# Kafka S08–S09

El broker canónico está en docker-compose.yml de la raíz, dentro de la red del proyecto. No se necesita otra composición DEV ni Kafka UI para ejecutar/verificar S12.

Topics: illari.orden.creada, illari.pago.resultado y sus sufijos .DLT. Órdenes usa clave de orden para creación/cancelación. Consumidores con auto-commit desactivado, ACK por registro e inbox durable; outbox confirma publicación. Replay ADMIN con motivo y auditoría.

Ver verification/README.md, docs/OBSERVABILIDAD.md y evidencias/S08-S09. Los datos del broker no se incluyen en el ZIP ni se borran al detener el proyecto.
