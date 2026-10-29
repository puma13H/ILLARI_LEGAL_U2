# Guion de sustentación desde la laptop

## Preparación personal

Conectar alimentación, abrir Docker Desktop y comprobar memoria/puertos. Tener credenciales propias en un gestor privado, tres sesiones/perfiles de navegador separados y un archivo de entorno privado. No mostrar tokens, pantallas de contraseñas, .env ni documentos reales. Preparar datos sintéticos. Identificar los puertos efectivos con Compose; los del laboratorio previo no son valores universales.

Ensayar antes, no reconstruir todo frente al jurado. Esperar Eureka y las rutas de negocio además de readiness. Abrir frontend, Eureka y Grafana autenticado. Guardar una copia local de las capturas de evidencia para contingencias. No borrar volúmenes para acelerar.

## Introducción grupal y reparto

La guía propone2–3min para alcance,2–3min para arquitectura y2–3min para reparto. Completar nombres reales y asignar a cada integrante una capacidad que pueda defender y ejecutar personalmente; no inventar autoría. Después prever3min de demo y3min de preguntas por integrante.

Pitch sugerido: «Illari gestiona casos y permite contratar varios servicios juntos. Sobre la base distribuida añadimos seguridad por identidad/rol, comunicación resiliente, pagos simulados asíncronos, compensación y diagnóstico correlacionado. Mostraremos efectos persistidos y fallos recuperables».

## Secuencia reproducible

| Paso | Acción en vivo | Qué explicar / comprobar | Alternativa si falla |
|---|---|---|---|
|1| Mostrar `docker compose ... ps`, Eureka y diagrama | Gateway, cuatro servicios, jurídico, IdP, broker y bases propias | Estado Docker y diagrama local; declarar fecha de la evidencia |
|2| Login CLIENTE por Keycloak | Code+S256, sesión/roles, API solo por Gateway | Captura01 de Keycloak y resultados HTTP; no simular un login exitoso |
|3| ADMIN/ABOGADO: caso y documentos sintéticos | Identidad jurídica, asignación y permiso propio | Capturas/matriz A conservadas en desarrollo, evidencia C y contrato HTTP |
|4| CLIENTE: elegir2 servicios, cantidades2/1 | Precios autorizados, total310, pago simulado | Compra53 preservada como evidencia anterior; aclarar que no se creó en esta demo |
|5| Confirmar y abrir Mis solicitudes | PENDIENTE_PAGO→PAGADA mediante Kafka, un pago por orden | Captura03 y extracto de persistencia; no declarar prueba nueva |
|6| Mostrar eventId/inbox/outbox/offset | Productor y consumidor independientes; al menos una vez | JSON de offsets y traza en evidencias/S08-S09 |
|7| Repetir la misma compra HTTP con su clave en laboratorio | Misma respuesta/orden; con otro cuerpo409 | Resultados de concurrencia/replay B; no ejecutar etapas fuera de orden |
|8| Cancelar una fixture nueva aprobada | CANCELACION_PENDIENTE; luego CANCELADA y REEMBOLSADO simulado | Resumen de órdenes54/57/63; nunca cancelar53 |
|9| Detener SOLO Catálogo del proyecto de prueba | 503 controlado, Retry/circuito y alertas; otros servicios siguen | Instantáneas de fallo B si no hay tiempo para esperar |
|10| Restaurar Catálogo y hacer llamadas válidas | HALF_OPEN→CLOSED, no reset mágico ni datos ficticios | Métricas recovered y explicación de tiempos |
|11| Grafana/Loki con traceId técnico de una compra | Métricas y logs de más de2 servicios; distinguir orderId/eventId |22 consultas guardadas y traza; no llamarlas capturas visuales de Grafana |
|12| Cerrar con decisiones y límites | Reservas/bloqueo en PostgreSQL, snapshot histórico, identidad por sub | Informe y matriz local; Usuarios fuera del CRUD Angular |

Los escenarios de corte Kafka/DLT requieren un turno aparte y más de unos segundos para backoff/alertas. Preparar otra fixture con el verificador B; explicar siempre que hace escrituras y cortes en laboratorio etiquetado. No ejecutar sus etapas de consistencia sobre la orden53 ni sobre datos reales. Todos los cortes deben restaurarse en `finally`.

## Comandos seguros para mostrar

```powershell
docker compose --env-file C:\privado\entrega.env -p illari-fase3-entrega -f docker-compose.yml -f obs/compose.s12.yml --profile ui --profile obs ps
docker compose --env-file C:\privado\entrega.env -p illari-fase3-entrega stop servicio-legal-ms
docker compose --env-file C:\privado\entrega.env -p illari-fase3-entrega start servicio-legal-ms
```

Estos comandos no contienen secretos; la ruta del archivo privado no equivale a mostrarlo. Durante el corte tener preparado el comando de restauración y comprobar primero el nombre exacto del proyecto. Para una prueba automatizada, usar los verificadores con guardas y los parámetros documentados; no copiar payloads ni JWT del informe.

## Preguntas difíciles y respuestas

1. **¿Por qué no una sola transacción para pago y orden?** Son bases/procesos independientes. Cada servicio confirma su transacción local; outbox+SAGA coordinan consistencia eventual y compensación.
2. **¿Exactamente una vez?** No en Kafka/transporte. Hay reentrega; la reserva de compra y los inbox hacen idempotente el efecto durable.
3. **¿Qué ocurre si se confirma DB y cae la publicación?** El outbox queda pendiente. Se reintenta con límites y puede requerir operación ADMIN; no se pierde el evento por depender de memoria.
4. **¿Y si llega aprobación después de cancelar?** Se mantiene cancelación pendiente hasta compensación. Una aprobación anterior no revierte revisión2; se valida pago/cliente/importe antes de transicionar.
5. **¿Por qué detalle relacional y también JSON?** Los detalles nuevos protegen multiplicidad e importes; el JSON conserva históricos durante la migración aditiva. No se reescribe historia borrándola.
6. **¿Cómo dos instancias evitan dos órdenes?** PK determinista sujeto+clave, INSERT con unicidad y bloqueo de reserva en PostgreSQL. No depende de un synchronized local.
7. **¿Un404 abre el circuito?** No: se clasifica como error de negocio. Se probaron ambas rutas. Timeout,5xx y disponibilidad sí alimentan resiliencia.
8. **¿Timeout y Circuit Breaker son lo mismo?** Timeout limita espera de una llamada; el circuito evita seguir invocando ante suficiente fallo. Retry agrega intentos acotados; no debe multiplicar cobros.
9. **¿Basta Gateway?** No. Cada backend valida JWT y el acceso al recurso. Se probó acceso directo con401/403 y ocultamiento404 en recursos ajenos.
10. **¿Por qué el cliente es String/sub?** El subject del issuer es la identidad estable; no se deduce por email ni se confunde con IDs numéricos de tablas locales.
11. **¿Dónde viven tokens y clave de compra?** Tokens solo en memoria; verifier/state temporalmente en sessionStorage para PKCE. El intento de compra guarda solicitud/clave para reintentar, nunca JWT.
12. **¿Qué demuestra un panel?** Una consulta con datos reales y correlación observable. Un cero sano es distinto de «sin datos»; se guardaron disparos reales de alertas.
13. **¿La renovación aumenta privilegios?** No modifica roles ni credenciales. Rota tokens ante Keycloak y verifica identidad/issuer/expiración; si falla, se vuelve al login.
14. **¿Por qué no hay CRUD Usuarios?** La identidad se administra en Keycloak. No se creó otro almacén de contraseñas para llenar una pantalla; el alcance está declarado.
15. **¿Qué no está probado?** HA, pérdida del host, carga y cobro bancario. La defensa personal, publicación y reconstrucción en otra laptop no se sustituyen por esta evidencia.

## Cierre individual

Cada integrante debe explicar una decisión propia, mostrar su aporte comprobable y responder al menos una pregunta. Completar nombres, responsabilidades y relato real de cambios antes de presentar. No leer respuestas que no se comprenden ni atribuirse cambios ajenos.
