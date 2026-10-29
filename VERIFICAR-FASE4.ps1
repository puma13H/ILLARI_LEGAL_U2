param(
    [Parameter(Mandatory=$true)][string]$EnvFile,
    [string]$ProjectName='illari-fase3-audit',
    [string]$Python='python',
    [string]$OutputDirectory=(Join-Path $PSScriptRoot 'verification-results/fase4')
)
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    if($ProjectName -notmatch '^illari-fase3-[a-z0-9-]+$'){throw 'Solo se permiten proyectos de auditoria illari-fase3-*'}
    $directory=Join-Path $OutputDirectory ((Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $composeArgs=@('compose','--env-file',(Resolve-Path -LiteralPath $EnvFile).Path,'-p',$ProjectName,
        '-f','docker-compose.yml','-f','obs/compose.s12.yml','--profile','obs')
    & docker @composeArgs config -q
    if($LASTEXITCODE -ne 0){throw 'Compose invalido'}
    & docker @composeArgs exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
    if($LASTEXITCODE -ne 0){throw 'promtool check config fallo'}
    & docker @composeArgs exec -T -w /etc/prometheus prometheus promtool test rules alerts.test.yml
    if($LASTEXITCODE -ne 0){throw 'promtool test rules fallo'}
    & $Python verification/infra/verificar_observabilidad.py --env-file $EnvFile --project $ProjectName --output (Join-Path $directory 'resultados.json') 2>&1 | Tee-Object (Join-Path $directory 'ejecucion.log')
    if($LASTEXITCODE -ne 0){throw "Verificacion con fallos; revisar $directory"}
    Write-Output "Evidencias de observabilidad: $directory"
} finally {Pop-Location}
