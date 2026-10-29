# Instalación y ejecución desde cero

## Requisitos

Docker Desktop en ejecución con Compose v2, Java21, Maven3.9, Node24 y npm, PowerShell y Python3. Se verificó Angular con Node24.22.1. Backend jurídico compila para Java17 y ejecuta con Java21. Reservar memoria para varias JVM; no ejecutar otra composición con los mismos puertos.

Descomprimir en una carpeta local y trabajar desde su raíz. El nombre del directorio no determina el nombre del proyecto Docker cuando se especifica `-p`.

## Variables privadas y cuentas

Copiar `.env.s12.example` a una ubicación privada fuera del repositorio. Completar contraseñas independientes y URLs/puertos coherentes. Para un laboratorio nuevo, añadir `ILLARI_ENVIRONMENT=verification` y usar un nombre `illari-fase3-*`. No cambiar una contraseña ya asociada a un volumen para resolver un error. Keycloak no trae usuarios de aplicación ni contraseñas predeterminadas.

Ejemplo de variables públicas, no credenciales:

```text
COMPOSE_PROJECT_NAME=illari-fase3-entrega
ILLARI_ENVIRONMENT=verification
GATEWAY_PORT=18080
KEYCLOAK_PORT=18180
FRONTEND_PORT=4200
KEYCLOAK_PUBLIC_URL=http://localhost:18180
FRONTEND_PUBLIC_URL=http://localhost:4200
GRAFANA_PORT=0
```

Configurar el archivo privado con el editor personal; no pegar sus valores en chats ni comandos que se guarden en documentos.

## Construir con pruebas

```powershell
Set-Location C:\ruta\ILLARI_LEGAL_U2_ENTREGA
.\BUILD-S12.ps1
```

BUILD-S12 prueba/empaqueta ocho aplicaciones Java, ejecuta la suite SAGA y prueba/compila Angular. Admite `-Maven`, `-MavenArguments`, `-Npm` y `-OmitirInstalacionNpm`; esta última opción solo reutiliza dependencias y no omite pruebas. Los JAR y bundle se generan localmente y por eso no se distribuyen. Dockerfiles de runtime consumen esos artefactos verificados.

## Iniciar una instalación nueva

```powershell
.\INICIAR-U2-S12.ps1 -EnvFile C:\privado\entrega.env -ProjectName illari-fase3-entrega
.\INICIAR-OBS-S12.ps1 -EnvFile C:\privado\entrega.env -ProjectName illari-fase3-entrega -Build
```

El primer script valida variables, puertos y artefactos y espera readiness. El segundo requiere el PostgreSQL existente etiquetado y añade el overlay de observabilidad al mismo proyecto.

El bootstrap `database/s12/00-init.sh` solo actúa sobre almacenamiento PostgreSQL vacío. Crea seis bases/roles: illari_clientes, illari_servicios, illari_ordenes, illari_pagos, illari_juridico e illari_keycloak. Órdenes aplica base→04→05→06; Pagos base→06; Jurídico01→07. Después se usa `ddl-auto=validate`. No ejecutar el bootstrap manualmente sobre bases anteriores.

Para datos existentes: identificar bases/esquemas, respaldar con `RESPALDAR-BASE-S12.ps1 -VerifyRestore`, inspeccionar el respaldo restaurado y solicitar autorización antes de cualquier migración incompatible. Esta entrega no migra ni importa datos existentes automáticamente.

## Configurar los tres perfiles

Crear usuarios propios en Keycloak y asignar ADMIN, ABOGADO o CLIENTE. El administrador de Keycloak y ADMIN de la aplicación son permisos diferentes. Conservar credenciales fuera del paquete.

El backend jurídico necesita una identidad local vinculada explícitamente al issuer/sub. En una base nueva se crea primero el operador jurídico ADMIN y se revisa su rol; después se vincula mediante `POST /api/identidades/vinculos` con JWT ADMIN. Cuerpo: `{"userId": ID_LOCAL_REVISADO, "subject": "SUB_REVISADO"}`. No seleccionar una cuenta local por email ni inventar un sub. Esa operación no cambia contraseñas y rechaza reasignaciones con409. Clientes y abogados se crean mediante sus rutas autorizadas. La preparación del primer operador requiere revisión personal: no hay una cuenta universal oculta de arranque.

## Abrir y verificar

Angular4200, Gateway18080 y Keycloak18180 en el ejemplo. Los puertos de diagnóstico y Grafana pueden asignarse dinámicamente:

```powershell
docker compose --env-file C:\privado\entrega.env -p illari-fase3-entrega port legal-eureka 8761
docker compose --env-file C:\privado\entrega.env -p illari-fase3-entrega -f docker-compose.yml -f obs/compose.s12.yml --profile obs port grafana 3000
```

Verificar Gateway `/api/v1/servicios`, readiness y las aplicaciones Eureka antes de presentar. Crear una compra nueva en una instalación vacía; **la orden53 pertenece a la base histórica del laboratorio y no viene dentro del ZIP**.

Detener conservando datos: `docker compose --env-file C:\privado\entrega.env -p illari-fase3-entrega -f docker-compose.yml -f obs/compose.s12.yml --profile ui --profile obs stop`. Para reanudar contenedores ya construidos, usar `start --wait`; no borrar volúmenes.

En C se validaron las rutas/configuración y el build Angular desde esta carpeta y se reanudó el laboratorio existente. No se repitió una instalación vacía completa ni todos los builds Java sin cambios; la instalación anterior ya fue verificada en Fase3. Antes de la exposición ensayar la reconstrucción en la laptop elegida.
