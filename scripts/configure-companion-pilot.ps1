param([ValidateSet('Start', 'Stop')][string]$Mode = 'Start')

# Bounded natural-use pilot: one daytime proactive opening at most per day.
# Preserves the other interaction settings and never enables physical motion.
$ErrorActionPreference = 'Stop'
$baseUrl = 'http://127.0.0.1:8080'
$deviceId = '0c4d09ea-ff9b-4701-9702-428c01cac264'
$databaseContainer = 'stackchan-foundation-postgres-1'
$adminId = [guid]::NewGuid().ToString()
$adminName = 'companion-pilot-once-' + [guid]::NewGuid().ToString('N')
$adminPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$salt = [Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
$derived = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
    $adminPassword, $salt, 310000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32
)
$adminHash = '{pbkdf2@SpringSecurity_v5_8}' + [Convert]::ToHexString([byte[]]($salt + $derived)).ToLowerInvariant()
$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$headers = @{}
$adminCreated = $false
$before = $null
$changed = $false

function QueryDatabase([string]$Sql) {
    $result = $Sql | & docker.exe exec -i $databaseContainer psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed; private output suppressed.' }
    return ($result -join "`n").Trim()
}

function RequestBody($settings, [bool]$enabled, [bool]$pilotLimits) {
    return @{
        volumePercent = $settings.volumePercent
        nightMode = $settings.nightMode
        continuousConversationEnabled = $settings.continuousConversationEnabled
        followUpWindowSeconds = $settings.followUpWindowSeconds
        dndEnabled = $settings.dndEnabled
        dndStart = $settings.dndStart
        dndEnd = $settings.dndEnd
        zoneId = $settings.zoneId
        missedReminderPolicy = $settings.missedReminderPolicy
        missedSnoozeMinutes = $settings.missedSnoozeMinutes
        proactiveEnabled = $enabled
        proactiveStart = $(if ($pilotLimits) { '10:00' } else { $settings.proactiveStart })
        proactiveEnd = $(if ($pilotLimits) { '18:00' } else { $settings.proactiveEnd })
        proactiveMinIntervalMinutes = $(if ($pilotLimits) { 90 } else { $settings.proactiveMinIntervalMinutes })
        proactiveDailyLimit = $(if ($pilotLimits) { 1 } else { $settings.proactiveDailyLimit })
        proactiveContent = $settings.proactiveContent
        proactivePersonalizationEnabled = $(if ($pilotLimits) { $false } else { $settings.proactivePersonalizationEnabled })
        silentPresenceEnabled = $settings.silentPresenceEnabled
    } | ConvertTo-Json -Compress
}

$row = QueryDatabase "select firmware_version || '|' || safety_state || '|' || body_motion_state || '|' || (last_seen_at > now() - interval '45 seconds') from devices where id = '$deviceId';"
if ($Mode -eq 'Start' -and $row -notin @('d18b3cd|motion_disabled|DISABLED|true', 'd18b3cd|motion_disabled|DISABLED|t')) {
    throw 'Pilot preflight requires the known online device with physical motion disabled.'
}
if ($Mode -eq 'Start' -and (QueryDatabase "select coalesce((select enabled::text from body_motion_auto_settings where device_id = '$deviceId'), 'false');") -ne 'false') {
    throw 'Automatic physical motion changed; pilot configuration stopped.'
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

    $url = "$baseUrl/api/v1/settings/interactions/$deviceId"
    $before = Invoke-RestMethod $url -WebSession $session
    if ($Mode -eq 'Start' -and $before.proactiveEnabled) {
        throw 'Proactive opening is already enabled; preserve the existing user configuration.'
    }
    $enabled = $Mode -eq 'Start'
    $body = RequestBody $before $enabled $enabled
    $null = Invoke-RestMethod $url -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body $body
    $changed = $true
    $after = Invoke-RestMethod $url -WebSession $session
    if ($after.proactiveEnabled -ne $enabled) { throw 'Proactive opening did not match the requested state.' }
    if ($enabled -and ($after.proactiveStart -notlike '10:00*' -or $after.proactiveEnd -notlike '18:00*' -or $after.proactiveDailyLimit -ne 1 -or $after.proactivePersonalizationEnabled)) {
        throw 'Pilot limits did not match the expected one-opening daytime configuration.'
    }
    Write-Output "Pilot proactive=$($after.proactiveEnabled) window=$($after.proactiveStart)-$($after.proactiveEnd) dailyLimit=$($after.proactiveDailyLimit) motion=unchanged"
}
catch {
    if ($changed -and $before) {
        try {
            $restore = RequestBody $before ([bool]$before.proactiveEnabled) $false
            $null = Invoke-RestMethod $url -Method Put -WebSession $session -Headers $headers -ContentType 'application/json' -Body $restore
        } catch { Write-Warning 'Interaction setting rollback needs manual inspection.' }
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
