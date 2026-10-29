# Fase5 — cierre de entrega y sustentación

Fecha:28/10/2026, America/Lima. **La Parte C termina con una entrega local independiente; no se publica ni se afirma aprobación académica ni100%.**

## Entrega y preservación

La carpeta es `ILLARI_LEGAL_U2_ENTREGA` y el ZIP hermano `ILLARI_LEGAL_U2_ENTREGA.zip`. El hash externo está en `ILLARI_LEGAL_U2_ENTREGA.zip.sha256`. La copia principal de desarrollo no se limpió destructivamente: sus fuentes, informes y evidencias originales se conservaron. Solo se añadieron la carpeta de entrega, el ZIP/hash y un informe de cierre.

Inventario inicial: **1062 archivos**, incluyendo551 generados. La selección inicial copió380 fuentes/configuraciones/scripts/pruebas. Recuento final de entrega: **459 archivos**. Las decisiones por archivo y motivos están en `docs/INVENTARIO_ORIGEN.csv`, y los incluidos con SHA-256 en `MANIFIESTO_ENTREGA.json`. Los archivos históricos excluidos se conservan en desarrollo; no se borraron originales dudosos.

El archivo externo `ILLARI_LEGAL_U2_ENTREGA.zip.verificacion.json` registra CRC, igualdad de archivos con el manifiesto, validación estática del ZIP extraído y su Compose. La comparación verificó510 archivos originales sin cambios y175 fuentes Java idénticas a desarrollo. No se distribuyen node_modules, target, dist, caches, .env privados, backups, documentos jurídicos ni credenciales de pruebas. La plantilla segura exige completar contraseñas fuera del proyecto. Los tokens sintéticos construidos por los tests no son credenciales de acceso a sistemas reales. La evidencia copiada se sanitiza y registra procedencia; las capturas originales permanecen intactas en desarrollo.

## Correcciones finales de la copia de entrega

- Mensajes globales usa CasoService/MensajeService: lista autorizada, consulta y envío persistente por caso; errores y prevención de doble envío. Se eliminaron conversaciones de muestra.
- Calendario consulta fechas de casos autorizados y la agenda general solo para ADMIN. ADMIN puede crear un evento usando la API existente; valida título/intervalo en el formulario. No se añadió un planificador avanzado ni se amplió el permiso del backend.
- Usuarios informa explícitamente que no existe CRUD global en Angular y que Keycloak administra cuentas/roles. No se presenta un placeholder como funcionalidad operativa ni se construye otro almacén de credenciales.
- Dashboard usa nombres/estados/fechas de casos reales en vez de «Ahora»/«Hace1h» o actividad de usuarios inventada. Se retiraron búsquedas sin implementación y el contador fijo de notificaciones de Tienda.
- Contraste de pago/rótulos y ancho mínimo de barras laterales corregidos; menú adaptable y contenido con min-width0. Las capturas nuevas se tomaron en1280px; no se acredita una matriz completa de dispositivos.
- Sesión se renueva ante Keycloak antes de expirar, con refresh single-flight, identidad estable, rotación en memoria y protección frente a respuestas tardías después de logout. Si no puede renovarse, vuelve al login; no guarda JWT en disco ni modifica contraseñas. El nonce se exige en login; en refresh puede faltar según el protocolo, pero si se incluye debe coincidir.
- Se externalizaron tres contraseñas literales de perfiles DEV solo en el export. S12 ya exige variables y mantiene validación de esquema.

No cambiaron las fuentes Java de negocio, eventos, migraciones ni la SAGA aprobada en B. Se conservaron los verificadores Python en verification/infra y se añadió verification/entrega para empaquetado/documentación; no se eliminaron herramientas por su lenguaje. Los wrappers antiguos siguen por compatibilidad, con un índice que aclara sus efectos.

## Pruebas nuevas y resultados conservados

| Comprobación | Resultado |
|---|---|
| Angular desde la carpeta de entrega | **51 aprobadas,0 fallidas,13 archivos**; incluye las37 de A |
| Build Angular de entrega | Correcto; bundle servido por frontend Docker |
| Navegador C | **9 observaciones favorables**: CLIENTE/ADMIN/ABOGADO y módulos nuevos; envío real por caso; evento ADMIN guardado y recargado; compra53 intacta; alcance Usuarios visible |
| Renovación real | CLIENTE siguió consultando una ruta protegida tras **511s sin nuevo login**; no se inspeccionaron ni exportaron tokens |
| Compose desde entrega | Configuración válida; frontend construido desde esa carpeta;17 contenedores en ejecución,15 healthy y2 sin healthcheck |
| Smoke y paquete | **19 aprobadas,0 fallidas** desde entrega; JSON final incluido y extracción/hashes en el registro externo del ZIP |
| Java en C | No reejecutado: fuentes de negocio sin cambios; se conserva package64/0 de Órdenes en B y evidencia de otros módulos |
| A/B | Se preservan compra53, capturas y resultados originales; no se recreó la compra ni se repitieron todas las matrices costosas |

Un intento intermedio de tests compiló45 casos aprobados y dejó1 archivo de pruebas sin ejecutar por un mock de módulo incompatible; el resultado final ejecutó las6 pruebas restantes y aprobó51/51. También se conserva evidencia intermedia de desarrollo: un enlace temporal de dependencias requirió permisos; se corrigieron errores de plantilla/tipado de las nuevas pruebas y un mock de módulo incompatible con la compilación de tests de Angular. El resultado posterior fue51/0; no se omitió ninguna prueba para conseguirlo.

Al iniciar la prueba visual se detectó que los contenedores del laboratorio estaban detenidos desde unas2horas antes, salvo frontend. Se arrancaron los contenedores existentes con `start --wait`; no se borraron/recrearon bases ni credenciales. El primer `up --wait frontend` terminó unhealthy porque Gateway estaba detenido. `start --wait` recuperó el stack y el smoke posterior pasó17/17. El rechazo inicial de conexión a Keycloak se documenta como incidencia del entorno, no como login aprobado.

## Evidencia y documentación

Se incluyen README, producto U2 adaptado, informe final, matriz S12, checklist, instalación, solución de errores, guion con preguntas difíciles, tres diagramas SVG reales y documentación HTML local navegable en docs/sitio. No es documentación publicada en Internet. Las guías oficiales U2/S12 se consultaron nuevamente con HTTP200.

Evidencias separadas por S06, S07, S08-S09, S10 y S11-S12. B aporta matriz251/0, consistencia31/0, dos JVM17/0, diagnóstico151/0, validación33/0 y cierre34/0; son etapas con preparación/repeticiones, no cantidades de casos únicos que se deban sumar. Sus22 paneles se verificaron por consultas, no por nuevas capturas visuales de Grafana. C agrega pruebas y capturas de los componentes cambiados.

## Funcionalidades completas y límites

El flujo técnico principal cuenta con autenticación/roles, gestión jurídica, carrito N, compra/pago simulado, idempotencia, SAGA/compensación, recuperación y diagnóstico. Los resultados B verifican esas capacidades en Docker; C corrige el cierre de frontend y prepara una entrega reproducible.

Usuarios global no tiene CRUD Angular. Calendario es agenda/lista de fechas y no planificación avanzada. No hay cobro bancario, HA, carga, Tempo ni Alertmanager. No se repitió la instalación vacía completa desde esta copia; se validaron estructura, rutas, comandos, build afectado y operación sobre laboratorio existente. Los datos históricos del laboratorio, incluida53, no forman parte del ZIP.

## Acciones personales pendientes

1. Extraer el ZIP y ensayar BUILD-S12 en la laptop elegida antes de la exposición.
2. Preparar archivo privado, cuentas propias y vínculos issuer/sub revisados. No reutilizar credenciales del laboratorio ni adivinar IDs.
3. Completar nombres/aportes reales, pitch y defensa individual; ensayar los tiempos de Eureka, circuito y alertas.
4. Abrir Grafana y comprobar sus vistas en vivo; mantener como contingencia las consultas/capturas reales indicando su fecha.
5. Revisar Git/topics y autorizar expresamente cualquier publicación. En este workspace no existe .git; no se inventaron commits ni staging y no hubo push.

El proyecto queda preparado para revisión y ensayo de la entrega local. El docente aún debe evaluar la demostración, autoría y sustentación; no se asigna una nota ni se declara todo aprobado automáticamente.
