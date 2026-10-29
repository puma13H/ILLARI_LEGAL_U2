param(
    [string]$Maven = 'mvn',
    [string[]]$MavenArguments = @(),
    [string]$Npm = 'npm.cmd',
    [switch]$Postgres,
    [switch]$OmitirInstalacionNpm
)
$ErrorActionPreference = 'Stop'
$container = $null
$token = [guid]::NewGuid().ToString('N')
$encodingAnterior = $OutputEncoding
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Invoke-Maven([string]$Pom, [string[]]$Extra = @()) {
    & $Maven @MavenArguments -B -ntp -f $Pom @Extra
    if ($LASTEXITCODE -ne 0) { throw "Falló Maven en $Pom" }
}
function Invoke-TestSql([string]$Database, [string]$Sql, [switch]$DebeFallar) {
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell may surface native stderr as an ErrorRecord before checking the exit code.
        $ErrorActionPreference = 'Continue'
        $result = $Sql | & docker exec -i $container psql --no-psqlrc -U postgres -d $Database -v ON_ERROR_STOP=1 -At 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($DebeFallar) {
        if ($code -eq 0) { throw 'Una migración con histórico inválido debió fallar' }
    } elseif ($code -ne 0) { throw "Falló SQL de prueba en $Database" }
    return $result | ForEach-Object { $_.ToString() }
}

Push-Location $PSScriptRoot
try {
    foreach ($module in @('cliente-ms','servicio-legal-ms','orden-legal-ms','pago-ms')) {
        Invoke-Maven "services/$module/pom.xml" @('package')
    }
    Invoke-Maven 'verification/saga/pom.xml' @('test')

    if ($Postgres) {
        # Only this new, labelled, loopback-bound, RAM-backed container is ever stopped.
        # No compose commands and no existing database/volume are used.
        $container = "illari-fase2-test-$token"
        & docker run --detach --rm --name $container --label "illari.audit=$token" `
            --publish '127.0.0.1::5432' --tmpfs /var/lib/postgresql/data `
            -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=saga_orders postgres:16-alpine
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL temporal' }
        $ready = $false
        for ($i = 0; $i -lt 60; $i++) {
            & docker exec $container pg_isready -U postgres -d saga_orders *> $null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            Start-Sleep -Milliseconds 500
        }
        if (!$ready) { throw 'PostgreSQL temporal no está listo' }
        $port = (& docker inspect --format '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' $container).Trim()
        if ($LASTEXITCODE -ne 0 -or $port -notmatch '^\d+$') { throw 'Puerto temporal inválido' }
        Invoke-TestSql saga_orders 'CREATE DATABASE saga_payments; CREATE DATABASE migracion_invalida;'
        Invoke-TestSql saga_orders (Get-Content verification/postgres/orden-fase1.sql -Raw -Encoding UTF8)
        Invoke-TestSql saga_payments (Get-Content verification/postgres/pago-fase1.sql -Raw -Encoding UTF8)
        $fase1 = Get-Content database/04-fase1-saga.sql -Raw -Encoding UTF8
        $detalle = Get-Content database/05-fase2-compras-detalle.sql -Raw -Encoding UTF8
        $mensajeria = Get-Content database/06-fase2-mensajeria.sql -Raw -Encoding UTF8
        Invoke-TestSql saga_orders $fase1
        Invoke-TestSql saga_orders $detalle
        Invoke-TestSql saga_orders $detalle # Rerunning must preserve the two historical rows.
        foreach ($db in @('saga_orders','saga_payments')) {
            Invoke-TestSql $db $mensajeria
            Invoke-TestSql $db $mensajeria
        }
        $historico = Invoke-TestSql saga_orders @'
SELECT count(*)=2 AND sum(subtotal)=120.00 FROM orden_detalle WHERE orden_id=9000;
SELECT detalles_json::jsonb->0->>'nombreServicio'='Consulta histórica' FROM ordenes_legales WHERE id=9000;
'@
        if (@($historico | Where-Object { $_ -eq 't' }).Count -ne 2) { throw 'Se alteró el histórico de prueba' }
        Invoke-TestSql migracion_invalida (Get-Content verification/postgres/orden-fase1.sql -Raw -Encoding UTF8)
        Invoke-TestSql migracion_invalida $fase1
        Invoke-TestSql migracion_invalida @'
UPDATE ordenes_legales SET detalles_json='[{"idServicio":7,"nombreServicio":"No redondear","cantidad":2,"precioUnitario":1.001,"subtotal":2.002}]',total=2.00 WHERE id=9000;
'@
        Invoke-TestSql migracion_invalida $detalle -DebeFallar
        $rollback = Invoke-TestSql migracion_invalida @'
SELECT to_regclass('orden_detalle') IS NULL;
SELECT detalles_json::jsonb->0->>'precioUnitario'='1.001' FROM ordenes_legales WHERE id=9000;
'@
        if (@($rollback | Where-Object { $_ -eq 't' }).Count -ne 2) { throw 'No se verificó el rollback de la migración inválida' }
        Invoke-Maven 'verification/saga/pom.xml' @("-Dfase2.postgres=jdbc:postgresql://127.0.0.1:$port/",'test')
        Write-Output 'PostgreSQL: migraciones, conservación histórica, rollback y Saga verificados.'
    }
    Push-Location frontend
    try {
        if (!$OmitirInstalacionNpm) {
            & $Npm ci
            if ($LASTEXITCODE -ne 0) { throw 'Falló npm ci' }
        }
        & $Npm test
        if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas Angular' }
        & $Npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación Angular' }
    } finally { Pop-Location }
} finally {
    if ($container) {
        $owner = & docker inspect --format '{{index .Config.Labels "illari.audit"}}' $container 2>$null
        if ($LASTEXITCODE -eq 0 -and $owner -eq $token) { & docker stop $container }
    }
    $OutputEncoding = $encodingAnterior
    Pop-Location
}
