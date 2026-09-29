param(
    [ValidateSet('Start', 'Stop')][string]$Mode = 'Start',
    [ValidatePattern('^[A-Za-z0-9._-]{1,31}$')][string]$FirmwareVersion = 'd18b3cd'
)

# Switches the independent low-frequency automatic-motion setting.
# Start requires that the firmware is already explicitly armed in person.
$ErrorActionPreference = 'Stop'
$deviceId = '0c4d09ea-ff9b-4701-9702-428c01cac264'
$baseUrl = 'http://127.0.0.1:8080'
$databaseContainer = 'stackchan-foundation-postgres-1'
$adminId = [guid]::NewGuid().ToString()
$adminName = 'body-natural-once-' + [guid]::NewGuid().ToString('N')
$adminPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$salt = [Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
$derived = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
    $adminPassword, $salt, 310000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32
)
$adminHash = '{pbkdf2@SpringSecurity_v5_8}' + [Convert]::ToHexString([byte[]]($salt + $derived)).ToLowerInvariant()
$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$headers = @{}
$adminCreated = $false
$changed = $false

function QueryDatabase([string]$Sql) {
    $result = $Sql | & docker.exe exec -i $databaseContainer psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed; private output suppressed.' }
    return ($result -join "`n").Trim()
}

function DeviceState {
    return QueryDatabase "select firmware_version || '|' || safety_state || '|' || body_motion_state || '|' || body_calibrated || '|' || servo_feedback_supported || '|' || (last_seen_at > now() - interval '45 seconds') from devices where id = '$deviceId';"
}

if ($Mode -eq 'Start' -and (DeviceState) -notin @(
    "$FirmwareVersion|motion_armed|ARMED|true|true|true",
    "$FirmwareVersion|motion_armed|ARMED|t|t|t"
)) {
    throw 'Starting automatic body motion requires fresh ARMED, calibration and feedback state.'
}

try {
    $null = QueryDatabase "insert into admin_users(id, username, password_hash, created_at) values ('$adminId', '$adminName', '$adminHash', now());"
    $adminCreated = $true
    $csrf = Invoke-RestMethod "$baseUrl/api/v1/auth/csrf" -WebSession $session
    $headers = @{ $csrf.headerName = $csrf.token }
    $loginBody = @{ username = $adminName; password = $adminPassword } | ConvertTo-Json -Compress
    $null = Invoke-RestMethod "$baseUrl/api/v1/auth/login" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body $loginBody
    $csrf = Invoke-RestMethod "$baseUrl/api/v1/auth/csrf" -WebSession $session
    $headers = @{ $csrf.headerName = $csrf.token }

    $deviceUrl = "$baseUrl/api/v1/devices/$deviceId"
    $before = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -WebSession $session
    if ($Mode -eq 'Start' -and $before.enabled) { throw 'Automatic body motion is already enabled.' }
    $enabled = $Mode -eq 'Start'
    $body = @{ enabled = $enabled } | ConvertTo-Json -Compress
    $after = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body $body
    $changed = $true
    if ($after.enabled -ne $enabled) { throw 'Automatic body-motion switch did not match the requested value.' }
    if ($enabled -and (DeviceState) -notin @(
        "$FirmwareVersion|motion_armed|ARMED|true|true|true",
        "$FirmwareVersion|motion_armed|ARMED|t|t|t"
    )) {
        throw 'Device disarmed while automatic body motion was being enabled.'
    }
    if (!$enabled) {
        $null = Invoke-RestMethod "$deviceUrl/body-motion" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
    }
    Write-Output "Automatic body motion=$($after.enabled); device=$(DeviceState)"
}
catch {
    if ($changed -and $Mode -eq 'Start') {
        try {
            $null = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
        } catch { Write-Warning 'Automatic body-motion rollback needs inspection.' }
    }
    if ($Mode -eq 'Stop' -and $adminCreated -and $headers.Count -gt 0) {
        try {
            $null = Invoke-RestMethod "$deviceUrl/body-motion" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
        } catch { Write-Warning 'Physical motion stop needs inspection.' }
    }
    throw
}
finally {
    try {
        if ($adminCreated) { $null = Invoke-RestMethod "$baseUrl/api/v1/auth/logout" -Method Post -WebSession $session -Headers $headers }
    } catch { Write-Warning 'Temporary session logout could not be confirmed.' }
    if ($adminCreated) {
        $null = QueryDatabase "delete from admin_users where id = '$adminId' and username = '$adminName';"
        if ((QueryDatabase "select count(*) from admin_users where id = '$adminId';") -ne '0') {
            throw 'Temporary administrator cleanup failed.'
        }
    }
    $adminPassword = $null
    $adminHash = $null
    $loginBody = $null
}
