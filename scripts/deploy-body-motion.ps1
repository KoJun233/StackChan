# Replace the LAN server and console after a stopped-source backup and isolated V53 restore.
# Credentials remain in process memory; the release backup retains a DPAPI protected copy.
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceName = 'stackchan-foundation-server-1'
$candidate = 'stackchan-foundation-server:body002-think-7d8c55a'
$previousEnvironment = @{}
$stopped = $false
$replacementStarted = $false

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
    $deploymentValues = @{
        POSTGRES_PASSWORD = $values.SPRING_DATASOURCE_PASSWORD
        COMPANION_DEVICE_TOKEN_SECRET = $values.COMPANION_DEVICE_TOKEN_SECRET
        COMPANION_SECRETS_ENCRYPTION_KEY = $values.COMPANION_SECRETS_ENCRYPTION_KEY
        COMPANION_ADMIN_INITIAL_PASSWORD = $values.COMPANION_ADMIN_INITIAL_PASSWORD
        SPRING_PROFILES_ACTIVE = $values.SPRING_PROFILES_ACTIVE
        SERVER_FORWARD_HEADERS_STRATEGY = $values.SERVER_FORWARD_HEADERS_STRATEGY
    }
    foreach ($key in $deploymentValues.Keys) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $deploymentValues[$key], 'Process')
    }
    $compose = @('compose', '-p', 'stackchan-foundation', '-f', 'compose.yaml', '-f', 'compose.lan.yaml', '-f', 'compose.body-motion.yaml')
    $rawConfig = & docker.exe @compose config --format json 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Compose validation failed.' }
    $configuration = $rawConfig -join "`n" | ConvertFrom-Json -AsHashtable
    $rawImage = & docker.exe image inspect $candidate 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Candidate image missing.' }
    $candidateImage = ($rawImage -join "`n" | ConvertFrom-Json)[0]
    $expected = @{}
    foreach ($entry in $candidateImage.Config.Env) {
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
    Write-Output 'Preflight passed: existing LAN configuration and volumes preserved.'

    & docker.exe stop $sourceName > $null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stop the old server for backup.' }
    $stopped = $true
    $backupResult = & (Join-Path $PSScriptRoot 'verify-companion-full-restore.ps1') -CandidateImage $candidate -ExpectedSchema 53 | ConvertFrom-Json
    if ($backupResult.schema -ne 53 -or !$backupResult.databaseCountsMatch -or !$backupResult.syntheticAdminLogin) {
        throw 'Stopped-source restore verification failed.'
    }
    Write-Output "Stopped-source backup and isolated V53 restore passed: $($backupResult.backupVolume)"

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
    if (!$healthy) { throw 'Candidate health/version check failed. Do not start an old application against V53 automatically.' }
    $rawRunning = & docker.exe inspect $sourceName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the replacement server.' }
    $running = ($rawRunning -join "`n" | ConvertFrom-Json)[0]
    if ($running.Image -ne $candidateImage.Id -or $running.Config.Env -notcontains 'COMPANION_BUILD_VERSION=body002-think-7d8c55a') {
        throw 'Replacement image or build version does not match the candidate.'
    }
    Write-Output 'BODY-002 server health and version: ok'
}
catch {
    if ($stopped -and !$replacementStarted) { & docker.exe start $sourceName > $null }
    throw
}
finally {
    foreach ($key in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
    $rawSource = $null; $source = $null; $values = $null; $deploymentValues = $null
    $rawConfig = $null; $configuration = $null; $expected = $null
    Pop-Location
}
