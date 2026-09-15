# Deploy the reviewed LAN candidate using the existing container's configuration.
# No credentials are printed or written to a plaintext environment file.
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceName = 'stackchan-foundation-server-1'
$previousEnvironment = @{}
$stopped = $false
$replacementStarted = $false
Push-Location $repository
try {
    $rawSource = & docker.exe inspect $sourceName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Existing server inspection failed.' }
    $source = ($rawSource -join "`n" | ConvertFrom-Json)[0]
    if ($source.Config.Labels.'com.docker.compose.project' -ne 'stackchan-foundation') { throw 'Unexpected Compose project.' }
    $values = @{}
    foreach ($entry in $source.Config.Env) {
        $parts = $entry -split '=', 2
        if ($parts.Length -eq 2) { $values[$parts[0]] = $parts[1] }
    }
    if ($values.COMPANION_LAN_DEVELOPMENT -ne 'true' -or $values.COMPANION_PRODUCTION -eq 'true') { throw 'This release script only preserves an existing LAN deployment.' }
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
    $compose = @('compose', '-p', 'stackchan-foundation', '-f', 'compose.yaml', '-f', 'compose.lan.yaml', '-f', 'compose.companion-v51.yaml')
    & docker.exe @compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose validation failed.' }
    & docker.exe stop $sourceName
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stop old server for a consistent backup.' }
    $stopped = $true
    & (Join-Path $PSScriptRoot 'verify-companion-full-restore.ps1') | Set-Content -LiteralPath 'server/target/companion-release-backup.log' -Encoding utf8
    # The complete isolated restore must return successfully before replacing server.
    $replacementStarted = $true
    & docker.exe @compose up -d --no-deps --no-build server
    if ($LASTEXITCODE -ne 0) { throw 'Candidate replacement failed; do not start an old application against V51 automatically.' }
    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 2
            if ($health.status -eq 'ok') { $healthy = $true; break }
        }
        catch { }
        Start-Sleep -Seconds 1
    }
    if (!$healthy) { throw 'Candidate health did not pass; retain pre-upgrade backup and investigate without automatic database overwrite.' }
    Write-Output 'Candidate server health: ok'
}
catch {
    if ($stopped -and !$replacementStarted) { & docker.exe start $sourceName > $null }
    throw
}
finally {
    foreach ($key in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
    $rawSource = $null; $source = $null; $values = $null; $deploymentValues = $null
    Pop-Location
}
