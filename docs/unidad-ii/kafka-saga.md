# Kafka, Saga y consistencia

## Flujo distribuido

1. El cliente realiza una compra en Angular.
2. Se crea una orden.
3. Se procesa el pago mediante mensajería asíncrona.
4. Se actualiza el estado de la orden.

## Resultados verificados

| Orden | Monto | Pago | Orden |
|---|---:|---|---|
| 1 | S/ 98.00 | VALIDADO | PAGADA |
| 2 | S/ 135.00 | VALIDADO | PAGADA |
| 3 | S/ 35.00 | VALIDADO | PAGADA |
| 4 | S/ 35.00 | FALLIDO | CANCELADA |

## Persistencia

Bases utilizadas:

- `illari_ordenes`
- `illari_pagos`

Se verificaron cuatro registros en `pagos_legales`
y cuatro registros en `ordenes_legales`.

La tabla `pago_inbox` contiene cuatro eventos procesados.

La tabla `pago_outbox` contiene cuatro eventos publicados.

La tabla `pago_dead_letter` no presentó registros
durante la consulta realizada.

## Pendiente

Documentar una prueba de reprocesamiento
sin duplicación de efectos.
