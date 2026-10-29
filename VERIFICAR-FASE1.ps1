param(
    [string]$Maven = 'mvn',
    [string]$Npm = 'npm.cmd'
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    foreach ($module in @('cliente-ms','orden-legal-ms','pago-ms')) {
        foreach ($goal in @('test','package')) {
            & $Maven -B -ntp -f "services/$module/pom.xml" $goal
            if ($LASTEXITCODE -ne 0) { throw "Falló $goal en $module" }
        }
    }
    & $Maven -B -ntp -f verification/saga/pom.xml test
    if ($LASTEXITCODE -ne 0) { throw 'Falló la integración de Saga' }
    Push-Location frontend
    try {
        & $Npm ci
        if ($LASTEXITCODE -ne 0) { throw 'Falló npm ci' }
        & $Npm test
        if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas Angular' }
        & $Npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación Angular' }
    } finally { Pop-Location }
} finally { Pop-Location }
