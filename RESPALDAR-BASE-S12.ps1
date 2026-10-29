param(
    [Parameter(Mandatory)][string]$Container,
    [Parameter(Mandatory)][string]$Database,
    [string]$DbUser='postgres',
    [string]$OutputDirectory=(Join-Path $PSScriptRoot 'backups'),
    [switch]$VerifyRestore
)
$ErrorActionPreference='Stop'
if($Container -notmatch '^[a-zA-Z0-9_.-]+$' -or $Database -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$' -or $DbUser -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$'){
    throw 'Identificadores de contenedor/base/usuario inválidos'
}
$runId=[guid]::NewGuid().ToString('N')
$destination=Join-Path $OutputDirectory ($Database+'-'+$runId)
New-Item -ItemType Directory -Path $destination -ErrorAction Stop > $null
$destination=(Resolve-Path -LiteralPath $destination).Path
$temporary="/tmp/illari-backup-$runId.dump"
$restoreContainer=$null
$schemaSql="SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position;"
$countSql=@'
SELECT format('SELECT %L || ''|'' || count(*) FROM %I.%I;',tablename,schemaname,tablename)
FROM pg_tables WHERE schemaname='public' ORDER BY tablename;
\gexec
'@
function Query([string]$Target,[string]$Db,[string]$User,[string]$Sql) {
    $data=$Sql | & docker exec -i $Target psql -X -U $User -d $Db -v ON_ERROR_STOP=1 -At
    if($LASTEXITCODE -ne 0){throw 'Falló la inspección SQL; no se aplicó ninguna migración'}
    return @($data)
}
try {
    $running=(& docker inspect --format '{{.State.Running}}' $Container).Trim()
    if($LASTEXITCODE -ne 0 -or $running -ne 'true'){throw 'La base debe estar disponible para un respaldo consistente'}
    $schema=Query $Container $Database $DbUser $schemaSql
    $counts=Query $Container $Database $DbUser $countSql
    $schema | Set-Content (Join-Path $destination 'schema.txt') -Encoding UTF8
    $counts | Set-Content (Join-Path $destination 'row-counts.txt') -Encoding UTF8
    & docker exec $Container pg_dump -w -U $DbUser -d $Database -Fc -f $temporary
    if($LASTEXITCODE -ne 0){throw 'Falló pg_dump; no se aplicó ninguna migración'}
    $archiveList=& docker exec $Container pg_restore --list $temporary
    if($LASTEXITCODE -ne 0){throw 'El archivo no tiene un catálogo pg_restore válido'}
    $archiveList | Set-Content (Join-Path $destination 'archive-list.txt') -Encoding UTF8
    $backup=Join-Path $destination 'database.dump'
    & docker cp ($Container+':'+$temporary) $backup
    if($LASTEXITCODE -ne 0){throw 'No se pudo copiar el respaldo'}
    $hash=(Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash
    $remoteHash=(& docker exec $Container sha256sum $temporary).Split(' ')[0].ToUpperInvariant()
    if($hash -ne $remoteHash){throw 'El SHA-256 no coincide con el archivo original'}
    $verified=$false
    if($VerifyRestore) {
        $restoreContainer="illari-restore-$runId"
        & docker run --detach --rm --name $restoreContainer --label "illari.backup-verify=$runId" --network none --tmpfs /var/lib/postgresql/data -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=verify_restore postgres:16-alpine > $null
        if($LASTEXITCODE -ne 0){throw 'No se pudo iniciar el destino aislado de restauración'}
        $ready=$false
        for($i=0;$i -lt 60;$i++){
            & docker exec $restoreContainer pg_isready -U postgres -d verify_restore *> $null
            if($LASTEXITCODE -eq 0){$ready=$true;break}
            Start-Sleep -Milliseconds 500
        }
        if(!$ready){throw 'El PostgreSQL aislado no alcanzó readiness'}
        & docker cp $backup ($restoreContainer+':/tmp/source.dump')
        if($LASTEXITCODE -ne 0){throw 'Falló copia para restauración'}
        & docker exec $restoreContainer pg_restore --no-owner --no-acl --exit-on-error -U postgres -d verify_restore /tmp/source.dump
        if($LASTEXITCODE -ne 0){throw 'El respaldo no pudo restaurarse'}
        $restoredSchema=Query $restoreContainer verify_restore postgres $schemaSql
        $restoredCounts=Query $restoreContainer verify_restore postgres $countSql
        if(Compare-Object $schema $restoredSchema){throw 'El esquema restaurado no coincide'}
        if(Compare-Object $counts $restoredCounts){throw 'Los conteos restaurados no coinciden'}
        $verified=$true
    }
    [pscustomobject]@{Container=$Container;Database=$Database;Sha256=$hash;Size=(Get-Item -LiteralPath $backup).Length;
        VerifiedRestore=$verified;CreatedUtc=[DateTime]::UtcNow.ToString('o');Schema='schema.txt';Counts='row-counts.txt'} |
        ConvertTo-Json | Set-Content (Join-Path $destination 'metadata.json') -Encoding UTF8
    Write-Output "Respaldo guardado y SHA-256 verificado: $destination"
    Write-Output "Restauración aislada comprobada: $verified. No se aplicaron migraciones a la base de origen."
} finally {
    if($restoreContainer){
        $owner=& docker inspect --format '{{index .Config.Labels "illari.backup-verify"}}' $restoreContainer 2>$null
        if($LASTEXITCODE -eq 0 -and $owner -eq $runId){& docker stop $restoreContainer > $null}
    }
}

