param(
    [ValidateSet('Status', 'ApiStatus', 'AutoOff', 'AutoWake', 'Calibrate', 'Enable', 'Disable', 'Stop', 'StopDuringNod', 'NOD_SMALL', 'LOOK_USER', 'THINK', 'DROWSY', 'WAKE')]
    [string]$Step = 'Status'
)

# One-device LAN physical verification. The default step only reads telemetry.
# Mutating steps use an ephemeral administrator and never print credentials.
$ErrorActionPreference = 'Stop'
$deviceId = '0c4d09ea-ff9b-4701-9702-428c01cac264'
$firmwareVersion = 'd18b3cd'
$baseUrl = 'http://127.0.0.1:8080'
$databaseContainer = 'stackchan-foundation-postgres-1'

function QueryDatabase([string]$Sql) {
    $result = $Sql | & docker.exe exec -i $databaseContainer psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed; private output suppressed.' }
    return ($result -join "`n").Trim()
}

function ReadDeviceState([int]$FreshnessSeconds = 45) {
    $row = QueryDatabase "select firmware_version || '|' || safety_state || '|' || body_motion_state || '|' || body_motion_supported || '|' || servo_feedback_supported || '|' || body_calibrated || '|' || body_last_failure_code || '|' || (last_seen_at > now() - make_interval(secs => $FreshnessSeconds)) from devices where id = '$deviceId';"
    $parts = $row -split '\|'
    foreach ($index in @(3, 4, 5, 7)) {
        if ($parts.Length -gt $index) {
            if ($parts[$index] -eq 't') { $parts[$index] = 'true' }
            if ($parts[$index] -eq 'f') { $parts[$index] = 'false' }
        }
    }
    if ($parts.Length -ne 8 -or $parts[0] -ne $firmwareVersion -or $parts[3] -ne 'true' -or $parts[7] -ne 'true') {
        throw 'Expected firmware, body capability or recent heartbeat is missing.'
    }
    return $parts
}

$state = ReadDeviceState
Write-Output "Device: firmware=$($state[0]) safety=$($state[1]) motion=$($state[2]) feedback=$($state[4]) calibrated=$($state[5]) failure=$($state[6])"
if ($Step -eq 'Status') { return }

$motions = @('NOD_SMALL', 'LOOK_USER', 'THINK', 'DROWSY', 'WAKE')
if ($motions -contains $Step -and
    ($state[1] -ne 'motion_armed' -or $state[2] -ne 'ARMED' -or
     $state[4] -ne 'true' -or $state[5] -ne 'true')) {
    throw 'Physical motion preflight requires armed, calibrated and live servo feedback.'
}
if ($Step -eq 'StopDuringNod' -and
    ($state[1] -ne 'motion_armed' -or $state[2] -ne 'ARMED' -or
     $state[4] -ne 'true' -or $state[5] -ne 'true')) {
    throw 'Stop-during-motion preflight requires armed, calibrated and live servo feedback.'
}
if ($Step -eq 'AutoWake' -and
    ($state[1] -ne 'motion_armed' -or $state[2] -ne 'ARMED' -or
     $state[4] -ne 'true' -or $state[5] -ne 'true')) {
    throw 'Automatic wake preflight requires armed, calibrated and live servo feedback.'
}
if ($Step -eq 'Enable' -and ($state[1] -ne 'motion_disabled' -or $state[5] -ne 'true')) {
    throw 'Enable preflight requires disabled motion and saved calibration.'
}
if ($Step -eq 'Calibrate' -and $state[1] -ne 'motion_disabled') {
    throw 'Calibration preflight requires disabled motion.'
}

$adminId = [guid]::NewGuid().ToString()
$adminName = 'motion-once-' + [guid]::NewGuid().ToString('N')
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

    $deviceUrl = "$baseUrl/api/v1/devices/$deviceId"
    switch ($Step) {
        'ApiStatus' {
            $devices = Invoke-RestMethod "$baseUrl/api/v1/devices" -WebSession $session
            $device = @($devices.devices | Where-Object { $_.id -eq $deviceId }) | Select-Object -First 1
            $automatic = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -WebSession $session
            if (!$device -or $device.firmwareVersion -ne $firmwareVersion -or
                !$device.online -or !$device.commandAvailable -or
                !$device.body.bodyMotionSupported -or $device.body.motionState -ne $state[2]) {
                throw 'Authenticated device API disagrees with the live telemetry preflight.'
            }
            Write-Output "API: firmware=$($device.firmwareVersion) online=$($device.online) commandAvailable=$($device.commandAvailable) motion=$($device.body.motionState) automatic=$($automatic.enabled)"
        }
        'AutoOff' {
            $automatic = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
            if ($automatic.enabled) { throw 'Automatic motion remained enabled.' }
            Write-Output 'Automatic motion disabled.'
        }
        'AutoWake' {
            $automatic = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -WebSession $session
            $runtimeUrl = "$baseUrl/api/v1/workday/$deviceId/runtime"
            $runtime = Invoke-RestMethod $runtimeUrl -WebSession $session
            if ($automatic.enabled -or $runtime.state -ne 'OFF') {
                throw 'Automatic wake preflight requires automatic motion off and workday off.'
            }
            $previous = QueryDatabase "select coalesce((select id::text from body_motion_commands where device_id = '$deviceId' and automatic = true and motion = 'WAKE' order by created_at desc limit 1), 'none');"
            try {
                $automatic = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":true}'
                if (!$automatic.enabled) { throw 'Automatic motion did not enable.' }
                $runtime = Invoke-RestMethod "$runtimeUrl`:start" -Method Post -WebSession $session -Headers $headers
                Write-Output "Workday start accepted: state=$($runtime.state)"
                $row = ''
                for ($attempt = 0; $attempt -lt 24; $attempt++) {
                    Start-Sleep -Seconds 1
                    $row = QueryDatabase "select coalesce((select id::text || '|' || status || '|' || coalesce(failure_code, '') from body_motion_commands where device_id = '$deviceId' and automatic = true and motion = 'WAKE' order by created_at desc limit 1), 'none');"
                    if ($row -ne 'none' -and !($row.StartsWith($previous + '|'))) {
                        $fields = $row -split '\|'
                        if ($fields[1] -notin @('SENT', 'ACCEPTED')) { break }
                    }
                }
                Write-Output "Automatic WAKE result: $row"
                if ($row -eq 'none' -or $row.StartsWith($previous + '|') -or
                    $fields[1] -ne 'COMPLETED' -or $fields[2] -ne 'NONE') {
                    throw 'Automatic WAKE did not return COMPLETED / NONE.'
                }
            }
            finally {
                $null = Invoke-RestMethod "$deviceUrl/body-motion/automatic" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
                $currentRuntime = Invoke-RestMethod $runtimeUrl -WebSession $session
                if ($currentRuntime.state -ne 'OFF') {
                    $null = Invoke-RestMethod "$runtimeUrl`:stop" -Method Post -WebSession $session -Headers $headers
                }
                $null = Invoke-RestMethod "$deviceUrl/body-motion" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
            }
        }
        'Calibrate' {
            $null = Invoke-RestMethod "$deviceUrl/commands/calibrate-body" -Method Post -WebSession $session -Headers $headers
            Write-Output 'Calibration command accepted; waiting for heartbeat result.'
        }
        'Enable' {
            $null = Invoke-RestMethod "$deviceUrl/body-motion" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":true}'
            Write-Output 'Enable command accepted; waiting for heartbeat result.'
        }
        'Disable' {
            $null = Invoke-RestMethod "$deviceUrl/body-motion" -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"enabled":false}'
            Write-Output 'Disable command accepted.'
        }
        'Stop' {
            $null = Invoke-RestMethod "$deviceUrl/commands/stop-motion" -Method Post -WebSession $session -Headers $headers
            Write-Output 'Stop command accepted.'
        }
        'StopDuringNod' {
            $command = Invoke-RestMethod "$deviceUrl/commands/body-motion" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"motion":"NOD_SMALL"}'
            if (!$command.id) { throw 'Nod command response omitted its ID.' }
            Write-Output "Stop-during-nod command issued: id=$($command.id) status=$($command.status)"
            Start-Sleep -Milliseconds 1500
            $null = Invoke-RestMethod "$deviceUrl/commands/stop-motion" -Method Post -WebSession $session -Headers $headers
            Write-Output 'Stop command accepted during nod.'
            for ($attempt = 0; $attempt -lt 22; $attempt++) {
                Start-Sleep -Seconds 1
                $result = Invoke-RestMethod "$deviceUrl/commands/body-motion/$($command.id)" -WebSession $session
                if ($result.status -notin @('SENT', 'ACCEPTED')) { break }
            }
            Write-Output "Stop-during-nod result: id=$($command.id) status=$($result.status) failure=$($result.failureCode)"
            if ($result.status -ne 'STOPPED' -or $result.failureCode -ne 'NONE') {
                throw 'Device did not report a clean STOPPED result for the interrupted nod.'
            }
        }
        default {
            $body = @{ motion = $Step } | ConvertTo-Json -Compress
            $command = Invoke-RestMethod "$deviceUrl/commands/body-motion" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body $body
            if (!$command.id) { throw 'Motion command response omitted its ID.' }
            Write-Output "Motion command issued: motion=$Step id=$($command.id) status=$($command.status)"
            for ($attempt = 0; $attempt -lt 22; $attempt++) {
                Start-Sleep -Seconds 1
                $result = Invoke-RestMethod "$deviceUrl/commands/body-motion/$($command.id)" -WebSession $session
                if ($result.status -notin @('SENT', 'ACCEPTED')) {
                    Write-Output "Motion result: motion=$Step id=$($command.id) status=$($result.status) failure=$($result.failureCode)"
                    break
                }
            }
            if ($result.status -in @('SENT', 'ACCEPTED')) {
                Write-Output "Motion result pending after 22 seconds: motion=$Step id=$($command.id) status=$($result.status)"
            }
        }
    }
}
finally {
    try {
        if ($csrf) { $null = Invoke-RestMethod "$baseUrl/api/v1/auth/logout" -Method Post -WebSession $session -Headers $headers }
    }
    catch { Write-Warning 'Temporary session logout could not be confirmed.' }
    if ($adminCreated) {
        $null = QueryDatabase "delete from admin_users where id = '$adminId' and username = '$adminName';"
        if ((QueryDatabase "select count(*) from admin_users where id = '$adminId';") -ne '0') {
            throw 'Temporary motion administrator cleanup failed.'
        }
    }
    $adminPassword = $null
    $adminHash = $null
    $loginBody = $null
}

if ($motions -contains $Step) {
    if ($result.status -ne 'COMPLETED' -or $result.failureCode -ne 'NONE') {
        throw "Motion did not complete normally: motion=$Step status=$($result.status) failure=$($result.failureCode)"
    }
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $state = ReadDeviceState 120
        if ($state[1] -eq 'motion_armed' -and $state[2] -eq 'ARMED') {
            $ready = $true
            break
        }
        if ($state[1] -eq 'motion_disabled' -or $state[2] -eq 'DISABLED') {
            throw "Device disabled motion after $Step."
        }
        Start-Sleep -Seconds 2
    }
    if (!$ready) { throw "Device did not report ARMED after $Step within 60 seconds." }
    Write-Output "Device after $Step`: safety=$($state[1]) motion=$($state[2]) feedback=$($state[4])"
}

if ($Step -notin @('Status', 'ApiStatus', 'AutoOff') -and $motions -notcontains $Step) {
    $confirmed = $false
    for ($attempt = 0; $attempt -lt 18; $attempt++) {
        Start-Sleep -Seconds 2
        # The device heartbeat can arrive around the 45-second preflight
        # boundary while the command itself has already taken effect.
        $state = ReadDeviceState 120
        if (($Step -eq 'Enable' -and $state[1] -eq 'motion_armed' -and $state[2] -eq 'ARMED') -or
            ($Step -in @('Disable', 'Stop', 'StopDuringNod', 'AutoWake') -and $state[1] -eq 'motion_disabled' -and $state[2] -eq 'DISABLED')) {
            $confirmed = $true
            break
        }
    }
    if (!$confirmed -and $Step -in @('Enable', 'Disable', 'Stop', 'StopDuringNod', 'AutoWake')) {
        throw "Device did not confirm $Step within 36 seconds."
    }
    Write-Output "Device after $Step`: safety=$($state[1]) motion=$($state[2]) feedback=$($state[4]) calibrated=$($state[5]) failure=$($state[6])"
}
