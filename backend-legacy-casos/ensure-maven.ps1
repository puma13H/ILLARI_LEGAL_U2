$ErrorActionPreference = "Stop"
$toolsDir = Join-Path $PSScriptRoot ".tools"
$mavenHome = Join-Path $toolsDir "apache-maven-3.9.9"
$mvnCmd = Join-Path $mavenHome "bin\mvn.cmd"

if (Test-Path $mvnCmd) {
    Write-Output $mvnCmd
    exit 0
}

Write-Host "Descargando Maven (solo la primera vez)..."
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
$zip = Join-Path $toolsDir "maven.zip"
$url = "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"

Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
Remove-Item $zip -Force

if (-not (Test-Path $mvnCmd)) {
    Write-Error "No se pudo instalar Maven"
}
Write-Output $mvnCmd
