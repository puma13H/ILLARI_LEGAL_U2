# Organización y política de entrega

No se limpió destructivamente la copia de desarrollo. El inventario inicial clasificó1062 archivos:351 de ejecución,29 de pruebas,10 documentos históricos,38 históricos adicionales,82 evidencias originales,551 generados y1 archivo local/plantilla anterior. La copia inicial seleccionó380 fuentes/configuraciones/scripts. Los recuentos finales y cada decisión se guardan en el manifiesto.

Se conservan ocho aplicaciones Java, Angular, base jurídica, migraciones, Compose/Dockerfiles, Keycloak, Kafka, SAGA, observabilidad actual y verificadores. Los ejemplos SQL clásicos con usuarios y las configuraciones antiguas de Promtail/observabilidad dev se conservan en desarrollo; no compiten con el despliegue canónico de entrega. No se fusionaron verificadores cuyos escenarios y efectos difieren.

No se incluyen bases/dumps/uploads, credenciales o estado privado de pruebas, fuentes de credenciales por defecto, dependencias, JAR, dist ni caches. Las cadenas ficticias de tests unitarios no autentican contra ningún sistema. Los tres perfiles dev con contraseña literal se externalizaron solo en la copia de entrega; el perfil S12 ya exige variables.

La selección de evidencia mantiene originales intactos. En JSON copiado se omiten nombres de acceso/contactos y rutas privadas; se conservan resultado, momento, estados e identificadores técnicos. Las capturas nuevas muestran roles/datos sintéticos, no identificadores de acceso, contraseñas ni tokens. El archivo de procedencia registra hashes antes/después; no se retocan visualmente las capturas para fingir resultados.

No existe .git en esta copia de trabajo; no se inventa estado de staging, commits ni topics. Se revisó y reforzó .gitignore de la entrega, no se inició un repositorio ni se publicó contenido. Los históricos permanecen archivables en desarrollo sin mover archivos de negocio o evidencias originales.
