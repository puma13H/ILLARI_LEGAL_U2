param(
    [Parameter(Mandatory=$true)][string]$EnvFile,
    [string]$ProjectName='illari-fase3-audit',
    [switch]$Build
)
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    & docker info --format '{{.ServerVersion}}'
    if($LASTEXITCODE -ne 0){throw 'Esta sesión no puede consultar la API de Docker. Comprueba permisos y contexto; no se modificó el laboratorio de observabilidad.'}
    if($ProjectName -notmatch '^illari-fase3-[a-z0-9-]+$'){throw 'Solo se admite el laboratorio aislado illari-fase3-*'}
    $baseArgs=@('compose','--env-file',(Resolve-Path -LiteralPath $EnvFile).Path,'-p',$ProjectName)
    $pg=(& docker @baseArgs ps -q postgres).Trim()
    if(!$pg){throw 'Arranca primero el PostgreSQL del laboratorio existente; este script no inicializa bases.'}
    $label=& docker inspect --format '{{index .Config.Labels "illari.environment"}}' $pg
    if($label.Trim() -ne 'verification'){throw 'El PostgreSQL no tiene la etiqueta de verificacion'}
    $obsArgs=$baseArgs+@('-f','docker-compose.yml','-f','obs/compose.s12.yml','--profile','ui','--profile','obs')
    & docker @obsArgs config -q
    if($LASTEXITCODE -ne 0){throw 'Variables o Compose no validos'}
    $start=@('up','-d','--wait','--wait-timeout','300')
    if($Build){$start+='--build'}
    & docker @obsArgs @start
    if($LASTEXITCODE -ne 0){throw 'El laboratorio no alcanzo readiness'}
    & docker @obsArgs port grafana 3000
} finally {Pop-Location}
