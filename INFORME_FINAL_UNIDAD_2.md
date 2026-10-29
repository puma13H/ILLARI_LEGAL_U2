# Informe final técnico — Unidad2

## Producto y arquitectura

Illari integra la gestión jurídica con la compra de varios servicios por un cliente. Angular se autentica con Keycloak mediante Authorization Code/PKCE y consume la API de negocio por nginx/Gateway. Config Server centraliza perfiles, Eureka resuelve nombres de servicios y Feign consulta Catálogo desde Órdenes. Los mensajes Kafka desacoplan Órdenes de Pagos.

Cada contexto comercial tiene base y credenciales propias. El backend jurídico conserva casos, abogados, clientes, documentos, agenda y mensajes con identidad local vinculada a issuer/sub. No se afirma que cada entidad jurídica sea un microservicio. [Diagramas](docs/ARQUITECTURA.md).

## Carrito y consistencia

Orden tiene varios detalles relacionales con cantidad, identificador del servicio, nombre/precio históricos y subtotal; el mismo servicio participa en órdenes distintas. La FK física une detalle y orden, no bases diferentes. Se conserva detalles_json histórico. El total se calcula con BigDecimal desde precios autorizados y se paga junto, no con varios pagos por solicitud.

Idempotency-Key se reserva por sujeto y clave en PostgreSQL; una huella distingue contenidos y responde409 ante reutilización incompatible. Orden, detalles, outbox y respuesta se confirman atómicamente. El consumidor confirma efecto e inbox juntos. El transporte es al menos una vez; el efecto de negocio es idempotente.

La SAGA no considera final una cancelación hasta recibir confirmación. Mantiene CANCELACION_PENDIENTE, usa revisiones y compensa un pago validado con reembolso simulado. Duplicados, eventos tardíos y resultados inconsistentes no producen una nueva aprobación ni un cobro duplicado.

## Verificación y alcance

Fase4 dejó111 casos Java/SAGA y27 Angular. A amplió Angular a37 y acreditó perfiles, caso, documentos y compra53 mediante navegador. B volvió a probar el módulo Java afectado con64 casos, seguridad real, dos JVM, fallos, replay,22 consultas Grafana y64 órdenes/64 pagos sin violaciones al cierre. Estas cifras corresponden a sus ejecuciones y no se suman como pruebas nuevas de C.

C conserva las fuentes Java de negocio y amplía Angular a51 pruebas aprobadas. Integra Mensajes por caso y Calendario con la API existente, elimina indicadores inventados y renueva la sesión en memoria antes de vencer. Se verificó su build desde la carpeta de entrega. Las evidencias nuevas y los límites se detallan en [FASE5_RESULTADOS](FASE5_RESULTADOS.md).

## Seguridad y diagnóstico

Gateway y cada backend validan JWT; issuer público y JWKS interno cumplen roles distintos. Se comprueban firma, expiración, audiencia y roles. La propiedad del recurso se valida además del rol; el404 de algunos recursos ajenos evita revelar su existencia. Los tokens no se persisten en localStorage/sessionStorage; el estado PKCE y el intento de compra sí se conservan temporalmente para su finalidad.

Prometheus consulta métricas y seis sondas readiness, Grafana tiene22 paneles y Loki recibe logs permitidos mediante Alloy. X-Trace-ID cruza HTTP, Feign y Kafka; eventId identifica una entrega lógica y ordenId identifica negocio. No se confunden. No hay spans OpenTelemetry, Alertmanager ni pasarela bancaria real.

## Límites académicos

Usuarios informa que no existe CRUD global en Angular; las identidades/roles se administran en Keycloak. Calendario es una lista cronológica/agenda, no un planificador avanzado. La publicación, exposición oral, autoría individual y nota corresponden al equipo/docente. El código y los ensayos de laboratorio no los sustituyen. No se declara100% ni aprobación automática de S12.
