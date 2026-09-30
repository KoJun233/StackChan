param(
    [switch]$Install,
    [ValidatePattern('^[A-Za-z0-9._-]{1,31}$')][string]$CurrentVersion = 'ffd0217',
    [ValidatePattern('^[A-Za-z0-9._-]{1,31}$')][string]$TargetVersion = 'd18b3cd',
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedSha256 = '6C6E6002841742CAB467EDFCDD81DEF9DABB68F51FDF9072653908E9EDE13983',
    [ValidateRange(1, 3145728)][int]$ExpectedSize = 1659952,
    [string]$ArtifactPath = (Join-Path $PSScriptRoot '..\firmware\build-body002-lan-http-quad\stackchan_firmware.bin')
)

# One-device LAN development OTA. Preflight is read-only; -Install uses the
# device, application artifact and NVS plan authorized for this task.
$ErrorActionPreference = 'Stop'
$deviceId = '0c4d09ea-ff9b-4701-9702-428c01cac264'
$baseUrl = 'http://127.0.0.1:8080'
$databaseContainer = 'stackchan-foundation-postgres-1'

function QueryDatabase([string]$Sql) {
    $result = $Sql | & docker.exe exec -i $databaseContainer psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Database operation failed; private output suppressed.' }
    return ($result -join "`n").Trim()
}

$artifact = Get-Item -LiteralPath $artifactPath
$bytes = [System.IO.File]::ReadAllBytes($artifact.FullName)
$embeddedVersion = [System.Text.Encoding]::ASCII.GetString($bytes, 48, 32).Trim([char]0)
$projectName = [System.Text.Encoding]::ASCII.GetString($bytes, 80, 32).Trim([char]0)
$actualSha256 = (Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA256).Hash
if ($embeddedVersion -ne $targetVersion -or $projectName -ne 'stackchan_firmware' -or
        $actualSha256 -ne $expectedSha256 -or $bytes.Length -ne $ExpectedSize) {
    throw 'Firmware version, project, size or SHA-256 differs from the approved candidate.'
}
$deviceRow = QueryDatabase "select firmware_version || '|' || safety_state || '|' || body_motion_state || '|' || application_ota_supported || '|' || (last_seen_at > now() - interval '2 minutes') from devices where id = '$deviceId';"
$deviceParts = $deviceRow -split '\|'
foreach ($index in @(3, 4)) {
    if ($deviceParts.Length -gt $index) {
        if ($deviceParts[$index] -eq 't') { $deviceParts[$index] = 'true' }
        if ($deviceParts[$index] -eq 'f') { $deviceParts[$index] = 'false' }
    }
}
if ($deviceParts.Length -ne 5 -or $deviceParts[0] -ne $currentVersion -or
        $deviceParts[3] -ne 'true' -or $deviceParts[4] -ne 'true' -or
        !(($deviceParts[1] -eq 'motion_disabled' -and $deviceParts[2] -eq 'DISABLED') -or
          ($deviceParts[1] -eq 'motion_armed' -and $deviceParts[2] -eq 'ARMED'))) {
    throw 'Device version, non-moving safety state, OTA capability or heartbeat freshness changed.'
}
$activeJobs = QueryDatabase "select count(*) from firmware_update_jobs where device_id = '$deviceId' and status in ('READY','INSTALLING');"
if ($activeJobs -ne '0') { throw 'An OTA job is already active for this device.' }
Write-Output "Preflight passed: device=$deviceId current=$currentVersion safety=$($deviceParts[2]) target=$targetVersion size=$($bytes.Length) sha256=$actualSha256; NVS is preserved by application OTA."
if (!$Install) { return }

$adminId = [guid]::NewGuid().ToString()
$adminName = 'ota-once-' + [guid]::NewGuid().ToString('N')
$adminPassword = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$salt = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
$derived = [System.Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
    $adminPassword, $salt, 310000, [System.Security.Cryptography.HashAlgorithmName]::SHA256, 32
)
$adminHash = '{pbkdf2@SpringSecurity_v5_8}' + [Convert]::ToHexString([byte[]]($salt + $derived)).ToLowerInvariant()
$adminCreated = $false
$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
try {
    $null = QueryDatabase "insert into admin_users(id, username, password_hash, created_at) values ('$adminId', '$adminName', '$adminHash', now());"
    $adminCreated = $true
    $csrf = Invoke-RestMethod "$baseUrl/api/v1/auth/csrf" -WebSession $session
    $headers = @{ $csrf.headerName = $csrf.token }
    $loginBody = @{ username = $adminName; password = $adminPassword } | ConvertTo-Json -Compress
    $null = Invoke-RestMethod "$baseUrl/api/v1/auth/login" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body $loginBody
    $csrf = Invoke-RestMethod "$baseUrl/api/v1/auth/csrf" -WebSession $session
    $headers = @{ $csrf.headerName = $csrf.token }

    if ($deviceParts[2] -eq 'ARMED') {
        $null = Invoke-RestMethod "$baseUrl/api/v1/devices/$deviceId/body-motion" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
        $disabled = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            Start-Sleep -Seconds 2
            $state = QueryDatabase "select safety_state || '|' || body_motion_state || '|' || (last_seen_at > now() - interval '2 minutes') from devices where id = '$deviceId';"
            if ($state -in @('motion_disabled|DISABLED|true', 'motion_disabled|DISABLED|t')) { $disabled = $true; break }
        }
        if (!$disabled) { throw 'Device did not confirm motion disabled before OTA.' }
        Write-Output 'Device confirmed motion disabled before OTA.'
    }

    $releases = Invoke-RestMethod "$baseUrl/api/v1/firmware/releases" -WebSession $session
    $release = @($releases.releases | Where-Object { $_.artifactSha256 -eq $actualSha256 }) | Select-Object -First 1
    if (!$release) {
        $release = Invoke-RestMethod "$baseUrl/api/v1/firmware/releases?version=$targetVersion" -Method Post -WebSession $session -Headers $headers -Form @{ artifact = $artifact }
    }
    if ($release.version -ne $targetVersion -or $release.artifactSha256 -ne $actualSha256 -or $release.artifactSize -ne $bytes.Length) {
        throw 'Imported release does not match the approved candidate.'
    }
    $jobBody = @{ deviceId = $deviceId; releaseId = $release.id; confirmedCurrentVersion = $currentVersion } | ConvertTo-Json -Compress
    $job = Invoke-RestMethod "$baseUrl/api/v1/firmware/jobs" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body $jobBody
    if ($job.deviceId -ne $deviceId -or $job.targetVersion -ne $targetVersion -or $job.fromVersion -ne $currentVersion) {
        throw 'Created OTA job does not match the approved device or versions.'
    }
    Write-Output "OTA job created: id=$($job.id) status=$($job.status) target=$($job.targetVersion)"
}
finally {
    try {
        if ($csrf) { $null = Invoke-RestMethod "$baseUrl/api/v1/auth/logout" -Method Post -WebSession $session -Headers $headers }
    }
    catch { Write-Warning 'Temporary session logout could not be confirmed.' }
    if ($adminCreated) {
        $null = QueryDatabase "delete from admin_users where id = '$adminId' and username = '$adminName';"
        $remaining = QueryDatabase "select count(*) from admin_users where id = '$adminId';"
        if ($remaining -ne '0') { throw "Temporary OTA administrator cleanup failed: $adminName" }
    }
    $adminPassword = $null
    $adminHash = $null
    $loginBody = $null
    $bytes = $null
}
