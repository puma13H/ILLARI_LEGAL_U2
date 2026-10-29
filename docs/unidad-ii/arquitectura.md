# Arquitectura

ILLARI LEGAL utiliza una arquitectura de microservicios.

## Componentes principales

| Componente | Función |
|---|---|
| Angular | Interfaz de usuario |
| Gateway | Entrada a los servicios |
| Keycloak | Autenticación y tokens |
| Microservicio de órdenes | Gestión de compras |
| Microservicio de pagos | Procesamiento de pagos |
| PostgreSQL | Persistencia |
| Kafka | Comunicación asíncrona |
| Prometheus | Recolección de métricas |
| Grafana | Visualización |

## Bases de datos verificadas

- `illari_ordenes`
- `illari_pagos`
- `illari_servicios`
- `illari_clientes`
- `illari_juridico`
- `illari_keycloak`
