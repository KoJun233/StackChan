# Replace the LAN server and console after a stopped-source backup and isolated restore.
# Credentials remain in process memory; the release backup retains a DPAPI protected copy.
param(
    [string]$CandidateImage = 'stackchan-foundation-server:body002-think-7d8c55a',
    [ValidatePattern('^[A-Za-z0-9._-]{1,80}$')][string]$BuildVersion = 'body002-think-7d8c55a',
    [ValidateRange(1, 999)][int]$ExpectedSchema = 53,
    [switch]$PreserveConsole
)
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceName = 'stackchan-foundation-server-1'
$candidate = $CandidateImage
$previousEnvironment = @{}
$stopped = $false
$replacementStarted = $false
$sourceSchema = $null

Push-Location $repository
try {
    $rawSource = & docker.exe inspect $sourceName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Existing server inspection failed.' }
    $source = ($rawSource -join "`n" | ConvertFrom-Json)[0]
    if ($source.Config.Labels.'com.docker.compose.project' -ne 'stackchan-foundation' -or !$source.State.Running) {
        throw 'Expected running Compose server was not found.'
    }
    $values = @{}
    foreach ($entry in $source.Config.Env) {
        $parts = $entry -split '=', 2
        if ($parts.Length -eq 2) { $values[$parts[0]] = $parts[1] }
    }
    if ($values.COMPANION_LAN_DEVELOPMENT -ne 'true' -or $values.COMPANION_PRODUCTION -eq 'true') {
        throw 'This release preserves only the existing LAN development mode.'
    }
    $oldImage = $source.Config.Image
    $oldVersion = $values.COMPANION_BUILD_VERSION
    if ($PreserveConsole) {
        $sourcePublic = & docker.exe exec $sourceName sha256sum /app/public/index.html
        $candidatePublic = & docker.exe run --rm --network none --entrypoint sha256sum $candidate /app/public/index.html
        if ($LASTEXITCODE -ne 0 -or $sourcePublic -ne $candidatePublic) {
            throw 'Candidate changes the deployed console shell; deployment stopped.'
        }
    }
    $deploymentValues = @{
        POSTGRES_PASSWORD = $values.SPRING_DATASOURCE_PASSWORD
        COMPANION_DEVICE_TOKEN_SECRET = $values.COMPANION_DEVICE_TOKEN_SECRET
        COMPANION_SECRETS_ENCRYPTION_KEY = $values.COMPANION_SECRETS_ENCRYPTION_KEY
        COMPANION_ADMIN_INITIAL_PASSWORD = $values.COMPANION_ADMIN_INITIAL_PASSWORD
        SPRING_PROFILES_ACTIVE = $values.SPRING_PROFILES_ACTIVE
        SERVER_FORWARD_HEADERS_STRATEGY = $values.SERVER_FORWARD_HEADERS_STRATEGY
    }
    $deploymentValues.STACKCHAN_CONSOLE_RELEASE_IMAGE = $candidate
    $deploymentValues.STACKCHAN_CONSOLE_RELEASE_VERSION = $BuildVersion
    foreach ($key in $deploymentValues.Keys) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $deploymentValues[$key], 'Process')
    }
    $releaseOverride = 'compose.console-ux.yaml'
    $compose = @('compose', '-p', 'stackchan-foundation', '-f', 'compose.yaml', '-f', 'compose.lan.yaml', '-f', $releaseOverride)
    $rawConfig = & docker.exe @compose config --format json 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Compose validation failed.' }
    $configuration = $rawConfig -join "`n" | ConvertFrom-Json -AsHashtable
    $rawImage = & docker.exe image inspect $candidate 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Candidate image missing.' }
    $candidateImageInfo = ($rawImage -join "`n" | ConvertFrom-Json)[0]
    $expected = @{}
    foreach ($entry in $candidateImageInfo.Config.Env) {
        $parts = $entry -split '=', 2
        if ($parts.Length -eq 2) { $expected[$parts[0]] = $parts[1] }
    }
    foreach ($key in $configuration.services.server.environment.Keys) {
        $expected[$key] = [string]$configuration.services.server.environment[$key]
    }
    foreach ($key in @($values.Keys) + @($expected.Keys) | Select-Object -Unique) {
        if ($key -ne 'COMPANION_BUILD_VERSION' -and $values[$key] -cne $expected[$key]) {
            throw "Unexpected environment change: $key. Deployment stopped."
        }
    }
    $oldMounts = @($source.Mounts | ForEach-Object { "$($_.Name)|$($_.Destination)|$($_.RW)" } | Sort-Object)
    $plannedMounts = @($configuration.services.server.volumes | ForEach-Object {
        $volumeName = $configuration.volumes[$_.source].name
        "$volumeName|$($_.target)|$(![bool]$_.read_only)"
    } | Sort-Object)
    if (Compare-Object $oldMounts $plannedMounts) { throw 'Volume layout changed; deployment stopped.' }
    $schemaResult = & docker.exe exec stackchan-foundation-postgres-1 psql -XAt -U stackchan -d stackchan -c "select max(version::integer) from flyway_schema_history where success and version ~ '^[0-9]+$';"
    if ($LASTEXITCODE -ne 0 -or $schemaResult -notmatch '^\d+$') { throw 'Cannot determine source schema.' }
    $sourceSchema = [int]$schemaResult
    if ($sourceSchema -gt $ExpectedSchema) { throw 'Candidate schema is older than source.' }
    Write-Output 'Preflight passed: existing LAN configuration and volumes preserved.'

    & docker.exe stop $sourceName > $null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stop the old server for backup.' }
    $stopped = $true
    $backupResult = & (Join-Path $PSScriptRoot 'verify-companion-full-restore.ps1') -CandidateImage $candidate -ExpectedSchema $ExpectedSchema | ConvertFrom-Json
    if ($backupResult.schema -ne $ExpectedSchema -or !$backupResult.databaseCountsMatch -or !$backupResult.syntheticAdminLogin) {
        throw 'Stopped-source restore verification failed.'
    }
    Write-Output "Stopped-source backup and isolated V$ExpectedSchema restore passed: $($backupResult.backupVolume)"

    $replacementStarted = $true
    & docker.exe @compose up -d --no-deps --no-build server
    if ($LASTEXITCODE -ne 0) { throw 'Candidate replacement failed. Keep the release backup for investigation.' }
    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 2
            if ($health.status -eq 'ok') { $healthy = $true; break }
        }
        catch { }
        Start-Sleep -Seconds 1
    }
    if (!$healthy) { throw 'Candidate health/version check failed. Do not start an old application against an upgraded schema automatically.' }
    $rawRunning = & docker.exe inspect $sourceName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the replacement server.' }
    $running = ($rawRunning -join "`n" | ConvertFrom-Json)[0]
    if ($running.Image -ne $candidateImageInfo.Id -or $running.Config.Env -notcontains "COMPANION_BUILD_VERSION=$BuildVersion") {
        throw 'Replacement image or build version does not match the candidate.'
    }
    Write-Output 'BODY-002 server health and version: ok'
}
catch {
    if ($stopped -and $replacementStarted -and $PreserveConsole -and $sourceSchema -eq $ExpectedSchema) {
        [Environment]::SetEnvironmentVariable('STACKCHAN_CONSOLE_RELEASE_IMAGE', $oldImage, 'Process')
        [Environment]::SetEnvironmentVariable('STACKCHAN_CONSOLE_RELEASE_VERSION', $oldVersion, 'Process')
        & docker.exe @compose up -d --no-deps --no-build server *> $null
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Original server rollback needs inspection.' }
    }
    if ($stopped -and !$replacementStarted) { & docker.exe start $sourceName > $null }
    throw
}
finally {
    foreach ($key in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
    $rawSource = $null; $source = $null; $values = $null; $deploymentValues = $null
    $rawConfig = $null; $configuration = $null; $expected = $null
    Pop-Location
}
