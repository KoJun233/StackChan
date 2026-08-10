param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-transport-stack-analysis'
)

$ErrorActionPreference = 'Stop'

$transportSourcePath = Join-Path $FirmwareDirectory 'main\device_transport.c'
$transportUsagePath = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir\device_transport.c.su"
$firmwareOtaUsagePath = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir\firmware_ota.c.su"

foreach ($path in @($transportSourcePath, $transportUsagePath, $firmwareOtaUsagePath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Transport stack-budget input was not found: $path. Build with -fstack-usage first."
    }
}

$source = Get-Content -LiteralPath $transportSourcePath -Raw
$taskStackMatch = [regex]::Match($source, '(?m)^#define\s+TRANSPORT_TASK_STACK_SIZE\s+(\d+)\r?$')
if (-not $taskStackMatch.Success) {
    throw 'TRANSPORT_TASK_STACK_SIZE must be an explicit numeric value'
}
$taskStackBytes = [int]$taskStackMatch.Groups[1].Value

$frameBytes = @{}
foreach ($usagePath in @($transportUsagePath, $firmwareOtaUsagePath)) {
    foreach ($line in Get-Content -LiteralPath $usagePath) {
        if ($line -match '(?:device_transport|firmware_ota)\.c:\d+:\d+:(?<name>[A-Za-z0-9_]+)\s+(?<bytes>\d+)\s+') {
            $frameBytes[$Matches.name] = [int]$Matches.bytes
        }
    }
}

$requiredFrames = @(
    'transport_task',
    'run_websocket_connection',
    'firmware_ota_install',
    'download_image'
)
foreach ($frame in $requiredFrames) {
    if (-not $frameBytes.ContainsKey($frame)) {
        throw "Transport stack-usage report is missing required frame: $frame"
    }
}

$knownLocalPathBytes = ($requiredFrames | ForEach-Object { $frameBytes[$_] } | Measure-Object -Sum).Sum

# The local .su reports do not include esp_http_client, TCP/IP, HTTP parsing,
# OTA flash helpers, authentication helpers, or their callbacks. Physical OTA
# overflowed a 10 KiB transport task before acknowledging the command, so keep
# the same independent 16 KiB external-call reserve used by the voice HTTP path.
$minimumExternalHeadroomBytes = 16384
$headroomBytes = $taskStackBytes - $knownLocalPathBytes
if ($headroomBytes -lt $minimumExternalHeadroomBytes) {
    throw "Transport task stack budget is unsafe: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, headroom=$headroomBytes bytes, requiredHeadroom=$minimumExternalHeadroomBytes bytes"
}

Write-Output "Transport task stack budget passed: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, externalHeadroom=$headroomBytes bytes"
