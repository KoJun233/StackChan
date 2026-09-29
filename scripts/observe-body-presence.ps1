param([ValidateRange(5, 1200)][int]$Seconds = 600)

# Polls only the stored presence boolean; no raw sensor values or credentials.
$ErrorActionPreference = 'Stop'
$databaseContainer = 'stackchan-foundation-postgres-1'
$deviceId = '0c4d09ea-ff9b-4701-9702-428c01cac264'
$deadline = [DateTimeOffset]::UtcNow.AddSeconds($Seconds)
$lastPresence = $null
$sawPresent = $false
$sawAbsentAfterPresent = $false

while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $sql = "select firmware_version || '|' || body_present || '|' || safety_state || '|' || body_motion_state || '|' || (last_seen_at > now() - interval '45 seconds') from devices where id = '$deviceId';"
    $row = ($sql | & docker.exe exec -i $databaseContainer psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan 2>&1) -join ''
    if ($LASTEXITCODE -ne 0) { throw 'Presence poll failed; database output suppressed.' }
    $parts = $row.Trim() -split '\|'
    if ($parts.Length -ne 5 -or $parts[0] -ne 'd18b3cd' -or $parts[4] -notin @('t', 'true') -or
        $parts[2] -ne 'motion_disabled' -or $parts[3] -ne 'DISABLED') {
        throw 'Firmware, heartbeat freshness or disabled-motion preflight changed.'
    }
    if ($parts[1] -ne $lastPresence) {
        if ($parts[1] -in @('t', 'true')) {
            $sawPresent = $true
            Write-Output "$([DateTimeOffset]::UtcNow.ToString('O')) present=true"
        } elseif ($parts[1] -in @('f', 'false')) {
            if ($sawPresent) {
                $sawAbsentAfterPresent = $true
                Write-Output "$([DateTimeOffset]::UtcNow.ToString('O')) present=false"
            }
        } else {
            throw 'Stored presence value was not a boolean.'
        }
        $lastPresence = $parts[1]
    }
    if ($sawAbsentAfterPresent) { break }
    Start-Sleep -Milliseconds 500
}

if (!$sawAbsentAfterPresent) { throw 'Timed out before a complete present=true then present=false observation.' }
