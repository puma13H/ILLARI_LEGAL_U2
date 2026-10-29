# Verificadores conservados

| Entrada | Uso | Efectos |
|---|---|---|
| BUILD-S12.ps1 | Reconstrucción completa con pruebas | Genera JAR y Angular; no aplica migraciones al laboratorio |
| verification/saga | Contextos Spring y Kafka de prueba; catálogo HTTP real, timeout, caída y recuperación | H2/PostgreSQL de laboratorio según parámetros; no base real. El ensayo HTTP usa exclusivamente H2 privada y puertos locales |
| verification/infra/verificar_s12.py | Matriz infraestructura/OIDC/roles/comercio/jurídico | Crea fixtures y reinicia solo componentes propios etiquetados |
| verification/infra/verificar_observabilidad.py | Diagnóstico de fallos y22 paneles | Cortes Catálogo/Kafka restaurados con finally |
| verification/infra/verificar_parte_b.py | Etapas security, consistency, multi-instance, diagnostics, payment-validation, closure | Estado privado fuera del proyecto; no ejecutar etapas sobre fixtures equivocadas |
| verification/infra/verificar_compra_navegador.py | Corroboración de compra hecha por navegador | Solo lectura; diseñado para fixture310.00 y dos servicios |
| verification/entrega/validar_entrega.py | Validación del export y smoke opcional | Estructura/privacidad; smoke de lectura en laboratorio |
| verification/entrega/generar_documentacion.py | HTML local offline | Genera docs/sitio; no publica nada |

Los scripts Python no se eliminaron por el lenguaje: son parte de la evidencia reproducible. Se conserva la separación entre suites porque sus fixtures y efectos son diferentes. Los wrappers VERIFICAR-FASE1/2/3/4 mantienen compatibilidad; no se los presenta como pruebas nuevas de C.

Para ensayos que escriben, usar exclusivamente proyectos `illari-fase3-*` con PostgreSQL etiquetado verification. Credenciales y estado de pruebas fuera del repositorio. Cada escenario requiere revisar los parámetros y el estado esperado; no encadenar etapas de B indiscriminadamente sobre una ejecución ya compensada.
