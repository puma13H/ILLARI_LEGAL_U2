param(
    [string]$EnvFile = '.env',
    [string]$ProjectName = 'illari-s12'
)
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    & docker info --format '{{.ServerVersion}}'
    if($LASTEXITCODE -ne 0){throw 'Esta sesión no puede consultar la API de Docker. Comprueba permisos y contexto; este error no demuestra que el motor esté apagado. No se inició ni modificó ningún contenedor.'}
    if(!(Test-Path -LiteralPath $EnvFile)){throw 'Falta el archivo privado de variables. Usa .env.s12.example como plantilla; no compartas sus valores.'}
    if($ProjectName -notmatch '^[a-z0-9][a-z0-9_-]{0,40}$'){throw 'Nombre de proyecto inválido'}
    $compose=@('compose','--env-file',(Resolve-Path -LiteralPath $EnvFile).Path,'-p',$ProjectName,'-f','docker-compose.yml')
    & docker @compose config --quiet
    if($LASTEXITCODE -ne 0){throw 'Compose inválido; no se inició ningún servicio'}
    $oldVolumes=@(& docker volume ls --filter "label=com.docker.compose.project=$ProjectName" --filter 'label=com.docker.compose.volume=postgres_data' --format '{{.Name}}')
    if($oldVolumes.Count){throw 'Hay un volumen de la composición anterior. Inspecciona y respalda sus bases antes de preparar su traslado; este script no lo migra.'}
    foreach($jar in @('services/cliente-ms/target/cliente-ms-1.0.0.jar','services/servicio-legal-ms/target/servicio-legal-ms-1.0.0.jar',
        'services/orden-legal-ms/target/orden-legal-ms-1.0.0.jar','services/pago-ms/target/pago-ms-1.0.0.jar',
        'backend-legacy-casos/target/backend-1.0.0.jar','infra/legal-config/target/legal-config-1.0.0.jar',
        'infra/legal-eureka/target/legal-eureka-1.0.0.jar','infra/legal-gateway/target/legal-gateway-1.0.0.jar',
        'frontend/dist/casos-legales-frontend/browser/index.html')) {
        if(!(Test-Path -LiteralPath $jar)){throw 'Faltan artefactos. Ejecuta BUILD-S12.ps1 con pruebas antes de crear las imágenes.'}
    }
    # Static public ports must be free, or already belong to this same compose project.
    $config=(& docker @compose config --format json | ConvertFrom-Json)
    foreach($property in $config.services.PSObject.Properties) {
        foreach($port in $property.Value.ports) {
            if(!$port.published -or $port.published -eq '0'){continue}
            $owners=@(& docker ps --filter "publish=$($port.published)" --format '{{.Label "com.docker.compose.project"}}')
            if($owners | Where-Object {$_ -ne $ProjectName}){throw "Puerto $($port.published) ocupado por otro proyecto Docker"}
            if(!$owners.Count -and (Get-NetTCPConnection -LocalPort ([int]$port.published) -State Listen -ErrorAction SilentlyContinue)){
                throw "Puerto $($port.published) ocupado por otro proceso"
            }
        }
    }
    & docker @compose --profile ui up -d --build --wait --wait-timeout 360
    if($LASTEXITCODE -ne 0){throw 'El stack no alcanzó readiness. Revisa logs del componente; no se borraron datos.'}
    & docker @compose --profile ui ps
    Write-Host 'Readiness comprobada. Seguridad y compra completa requieren VERIFICAR-FASE3.ps1; S12 no se declara terminada.'
} finally {Pop-Location}
