param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-voice-stack-analysis'
)

$ErrorActionPreference = 'Stop'

$voiceControlSourcePath = Join-Path $FirmwareDirectory 'main\voice_control.c'
$voiceControlUsagePath = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir\voice_control.c.su"
$voiceServiceUsagePath = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir\voice_service.c.su"

foreach ($path in @($voiceControlSourcePath, $voiceControlUsagePath, $voiceServiceUsagePath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Voice stack-budget input was not found: $path. Build with -fstack-usage first."
    }
}

$source = Get-Content -LiteralPath $voiceControlSourcePath -Raw
$taskStackMatch = [regex]::Match($source, '(?m)^#define\s+VOICE_TASK_STACK_SIZE\s+(\d+)\r?$')
if (-not $taskStackMatch.Success) {
    throw 'VOICE_TASK_STACK_SIZE must be an explicit numeric value'
}
$taskStackBytes = [int]$taskStackMatch.Groups[1].Value

$frameBytes = @{}
foreach ($usagePath in @($voiceControlUsagePath, $voiceServiceUsagePath)) {
    foreach ($line in Get-Content -LiteralPath $usagePath) {
        if ($line -match '(?:voice_control|voice_service)\.c:\d+:\d+:(?<name>[A-Za-z0-9_]+)\s+(?<bytes>\d+)\s+') {
            $frameBytes[$Matches.name] = [int]$Matches.bytes
        }
    }
}

$requiredFrames = @(
    'voice_task',
    'run_voice_turn',
    'voice_service_send_turn',
    'perform_request',
    'response_event_handler'
)
foreach ($frame in $requiredFrames) {
    if (-not $frameBytes.ContainsKey($frame)) {
        throw "Voice stack-usage report is missing required frame: $frame"
    }
}

$knownLocalPathBytes = ($requiredFrames | ForEach-Object { $frameBytes[$_] } | Measure-Object -Sum).Sum

# The local .su reports do not include esp_http_client, TCP/IP, HTTP parsing,
# authentication helpers, or their callbacks. A 12 KiB task overflowed on the
# first upload in physical testing, so retain a separate 16 KiB library reserve.
$minimumExternalHeadroomBytes = 16384
$headroomBytes = $taskStackBytes - $knownLocalPathBytes
if ($headroomBytes -lt $minimumExternalHeadroomBytes) {
    throw "Voice task stack budget is unsafe: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, headroom=$headroomBytes bytes, requiredHeadroom=$minimumExternalHeadroomBytes bytes"
}

Write-Output "Voice task stack budget passed: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, externalHeadroom=$headroomBytes bytes"
