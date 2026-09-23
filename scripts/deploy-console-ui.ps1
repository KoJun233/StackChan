# Replace only the console assets while retaining the running backend binary and configuration.
param([switch]$Rollback)
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceName = 'stackchan-foundation-server-1'
$candidate = if ($Rollback) { 'stackchan-foundation-server:companion-v51-20260914' } else { 'stackchan-foundation-server:console-ux-20260921' }
$version = if ($Rollback) { 'companion-v51-20260914' } else { 'console-ux-20260921' }
$previousEnvironment = @{}
Push-Location $repository
try {
    $raw = & docker.exe inspect $sourceName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the running server.' }
    $source = ($raw -join "`n" | ConvertFrom-Json)[0]
    if ($source.Config.Labels.'com.docker.compose.project' -ne 'stackchan-foundation') { throw 'Unexpected Compose project.' }
    if (!$source.State.Running) { throw 'The source server must be running for this console-only release.' }
    $values = @{}
    foreach ($entry in $source.Config.Env) {
        $parts = $entry -split '=', 2
        $values[$parts[0]] = $parts[1]
    }
    if ($values.COMPANION_LAN_DEVELOPMENT -ne 'true' -or $values.COMPANION_PRODUCTION -eq 'true') { throw 'Only the existing LAN deployment is supported.' }
    $sourceHash = & docker.exe exec $sourceName sha256sum /app/app.jar
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify the running backend binary.' }
    $candidateHash = & docker.exe run --rm --network none --entrypoint sha256sum $candidate /app/app.jar
    if ($LASTEXITCODE -ne 0 -or $sourceHash -ne $candidateHash) { throw 'Backend binary changed; this is not a console-only release.' }
    $deploymentValues = @{
        POSTGRES_PASSWORD = $values.SPRING_DATASOURCE_PASSWORD
        COMPANION_DEVICE_TOKEN_SECRET = $values.COMPANION_DEVICE_TOKEN_SECRET
        COMPANION_SECRETS_ENCRYPTION_KEY = $values.COMPANION_SECRETS_ENCRYPTION_KEY
        COMPANION_ADMIN_INITIAL_PASSWORD = $values.COMPANION_ADMIN_INITIAL_PASSWORD
        SPRING_PROFILES_ACTIVE = $values.SPRING_PROFILES_ACTIVE
        SERVER_FORWARD_HEADERS_STRATEGY = $values.SERVER_FORWARD_HEADERS_STRATEGY
        STACKCHAN_CONSOLE_RELEASE_IMAGE = $candidate
        STACKCHAN_CONSOLE_RELEASE_VERSION = $version
    }
    foreach ($key in $deploymentValues.Keys) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $deploymentValues[$key], 'Process')
    }
    $compose = @('compose', '-p', 'stackchan-foundation', '-f', 'compose.yaml', '-f', 'compose.lan.yaml', '-f', 'compose.console-ux.yaml')
    $rawConfig = & docker.exe @compose config --format json 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Compose validation failed.' }
    $configuration = $rawConfig -join "`n" | ConvertFrom-Json -AsHashtable
    $rawImage = & docker.exe image inspect $candidate 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Candidate image missing.' }
    $candidateImage = ($rawImage -join "`n" | ConvertFrom-Json)[0]
    $expected = @{}
    foreach ($entry in $candidateImage.Config.Env) {
        $parts = $entry -split '=', 2
        $expected[$parts[0]] = $parts[1]
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
    Write-Output 'Preflight passed: identical backend binary, existing configuration and volumes preserved.'
    & docker.exe @compose up -d --no-deps --no-build server
    if ($LASTEXITCODE -ne 0) { throw 'Server replacement failed. Retain both images and inspect state before retrying.' }
    $healthy = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 2
            if ($health.status -eq 'ok') { $healthy = $true; break }
        }
        catch { }
        Start-Sleep -Seconds 1
    }
    if (!$healthy) { throw 'Health check failed. The previous UI image remains available; no database restore was performed.' }
    Write-Output "Console release healthy: $version"
}
finally {
    foreach ($key in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
    $raw = $null; $source = $null; $values = $null; $deploymentValues = $null
    $rawConfig = $null; $configuration = $null; $expected = $null
    Pop-Location
}
