# Producto Unidad2 — ILLARI LEGAL

## Dominio y alcance

Illari administra casos jurídicos y permite que un cliente contrate varios servicios en una orden, con un pago conjunto simulado. Se mantiene el backend jurídico original y se separan los contextos comerciales, identidad e infraestructura. El resultado evaluable es el flujo ejecutable y sus evidencias, no una copia del dominio del ejemplo del docente.

| Capacidad de Unidad2 | Realización en Illari | Verificación disponible |
|---|---|---|
| Comunicación resiliente | Órdenes consulta Catálogo mediante Feign/Eureka, con espera e intentos acotados y respuesta503 ante indisponibilidad | Caída/pausa real, circuito abierto/recuperado y exclusión de negocio400 |
| Seguridad distribuida | Keycloak Code/PKCE, JWT validado por Gateway y cinco backends, roles e identidad jurídica estable | Matriz HTTP y navegador por perfil; recursos ajenos protegidos |
| Mensajería desacoplada | Comandos y resultados por Kafka, con outbox e inbox locales | Eventos/offsets y recuperación de ambas DLT |
| Consistencia eventual | Reservas de compra, transiciones, cancelación pendiente, revisiones y reembolso simulado | Concurrencia entre JVM, replays, duplicados/tardíos e invariantes SQL |
| Diagnóstico | Métricas/health en Prometheus,22 paneles Grafana y registros permitidos por Alloy/Loki | Consultas reales y alertas durante fallos con traceId común |
| Cliente integrado | Angular usa Gateway para negocio y Keycloak para identidad; carrito N, caso/documentos, mensajes y agenda según permisos | Compra53 preservada y verificaciones visuales;51 pruebas Angular en entrega |

La asociación orden_detalle implementa la multiplicidad entre órdenes y servicios sin FK entre bases independientes. Guarda cantidad y valores históricos y participa en la transacción de Orden. El proceso entre servicios usa SAGA, no una transacción ACID global.

## Evidencias, decisiones y límites

Consultar [matriz S12](MATRIZ_CUMPLIMIENTO_S12.md), [informe](INFORME_FINAL_UNIDAD_2.md), [resultados](FASE5_RESULTADOS.md) y [evidencias](evidencias/README.md). Las ejecuciones de distintas fases no se suman como si fueran pruebas nuevas. Se distingue JVM/embebido, HTTP, Docker, SQL y navegador.

Usuarios global no ofrece CRUD Angular; las identidades se administran en Keycloak. Agenda es una lista cronológica sobre API existente, no planificación avanzada. Pagos y reembolsos no procesan dinero real. Publicación, pitch, autoría y defensa individual siguen sujetos a la preparación del equipo y a la evaluación del docente; no se inventa una nota ni100% de finalización.

La estructura se contrastó con el [producto oficial U2](https://262dist.github.io/pagatu/proyecto-sello/u2-producto/) y la [evaluación S12](https://262dist.github.io/pagatu/sesiones/S12_Evaluacion_Unidad_2/), consultados el28/10/2026.
