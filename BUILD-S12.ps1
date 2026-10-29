param(
    [string]$Maven = 'mvn',
    [string[]]$MavenArguments = @(),
    [string]$Npm = 'npm.cmd',
    [switch]$OmitirInstalacionNpm,
    [string]$JavaHome
)
$ErrorActionPreference='Stop'
$previousJavaHome=$env:JAVA_HOME
$previousPath=$env:PATH
Push-Location $PSScriptRoot
try {
    # Validate the JDK used by Maven before generating any artifacts. A JRE or
    # Java 17 can otherwise fail late with "release version 21 not supported".
    if($JavaHome) {
        if(!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) {
            throw 'JavaHome debe apuntar a un JDK con bin/javac.exe.'
        }
        $env:JAVA_HOME=(Resolve-Path -LiteralPath $JavaHome).Path
        $env:PATH="$(Join-Path $env:JAVA_HOME 'bin');$previousPath"
    }
    $compiler=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/javac.exe'}else{'javac'}
    $compilerVersion=(& $compiler -version 2>&1 | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or $compilerVersion -notmatch 'javac\s+(\d+)' -or [int]$Matches[1] -lt 21) {
        throw 'S12 requiere JDK 21 o superior. Configure JAVA_HOME o use -JavaHome con la ruta del JDK 21. No se modificó la configuración global de Java.'
    }
    Write-Host "Compilador verificado: $compilerVersion"
    foreach($module in @('services/cliente-ms','services/servicio-legal-ms','services/orden-legal-ms','services/pago-ms',
                         'backend-legacy-casos','infra/legal-config','infra/legal-eureka','infra/legal-gateway')) {
        & $Maven @MavenArguments -B -ntp -f "$module/pom.xml" package
        if($LASTEXITCODE -ne 0){throw "Falló package con pruebas en $module"}
    }
    & $Maven @MavenArguments -B -ntp -f verification/saga/pom.xml test
    if($LASTEXITCODE -ne 0){throw 'Falló la compatibilidad Saga/Kafka de Fase 2'}
    Push-Location frontend
    try {
        if(!$OmitirInstalacionNpm){& $Npm ci;if($LASTEXITCODE -ne 0){throw 'Falló npm ci'}}
        & $Npm test
        if($LASTEXITCODE -ne 0){throw 'Fallaron las pruebas Angular'}
        & $Npm run build
        if($LASTEXITCODE -ne 0){throw 'Falló el build Angular'}
    } finally {Pop-Location}
} finally {
    Pop-Location
    $env:JAVA_HOME=$previousJavaHome
    $env:PATH=$previousPath
}

