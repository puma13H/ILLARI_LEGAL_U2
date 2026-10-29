param(
    [Parameter(Mandatory)][string]$EnvFile,
    [Parameter(Mandatory)][string]$ProjectName,
    [string]$Python='python',
    [string]$OutputDirectory=(Join-Path $PSScriptRoot 'verification-results/phase3')
)
$ErrorActionPreference='Stop'
if($ProjectName -notmatch '^illari-fase3-[a-z0-9-]+$'){
    throw 'Este verificador escribe fixtures. Solo admite un proyecto dedicado illari-fase3-*, no el entorno de datos reales.'
}
if(!(Test-Path -LiteralPath $EnvFile)){throw 'No existe el archivo privado de variables'}
$envPath=(Resolve-Path -LiteralPath $EnvFile).Path
New-Item -ItemType Directory -Path $OutputDirectory -Force > $null
$destination=(Resolve-Path -LiteralPath $OutputDirectory).Path
$runId=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$result=Join-Path $destination ($runId+'.json')
$log=Join-Path $destination ($runId+'.log')
Push-Location $PSScriptRoot
try {
    & docker compose --env-file $envPath -p $ProjectName config --quiet
    if($LASTEXITCODE -ne 0){throw 'Compose inválido'}
    # The Python verifier also checks the PostgreSQL environment label before any write.
    # It creates NEW test users, never resets credentials, and restarts only its own test containers.
    & $Python verification/infra/verificar_s12.py --project $ProjectName --env-file $envPath --output $result 2>&1 | Tee-Object -FilePath $log
    $code=$LASTEXITCODE
    if(Test-Path -LiteralPath $result){
        $summary=Get-Content -LiteralPath $result -Raw | ConvertFrom-Json
        Write-Output "Comprobaciones: $($summary.passed) aprobadas; $($summary.failed) fallidas. Resultados: $result"
    }
    if($code -ne 0){throw "La verificación no terminó correctamente. Revisa $log"}
    Write-Output 'No se declara S12 terminada. El proyecto de auditoría y sus volúmenes se conservan.'
} finally {Pop-Location}

