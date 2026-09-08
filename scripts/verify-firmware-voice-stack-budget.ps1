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
$uploadTaskStackMatch = [regex]::Match(
    $source,
    '(?m)^#define\s+VOICE_UPLOAD_TASK_STACK_SIZE\s+(\d+)\r?$'
)
$playbackTaskStackMatch = [regex]::Match(
    $source,
    '(?m)^#define\s+VOICE_PLAYBACK_TASK_STACK_SIZE\s+(\d+)\r?$'
)
if (-not $taskStackMatch.Success -or -not $uploadTaskStackMatch.Success -or
    -not $playbackTaskStackMatch.Success) {
    throw 'Voice, upload, and playback task stack sizes must be explicit numeric values'
}
$taskStackBytes = [int]$taskStackMatch.Groups[1].Value
$uploadTaskStackBytes = [int]$uploadTaskStackMatch.Groups[1].Value
$playbackTaskStackBytes = [int]$playbackTaskStackMatch.Groups[1].Value

$frameBytes = @{}
foreach ($usagePath in @($voiceControlUsagePath, $voiceServiceUsagePath)) {
    foreach ($line in Get-Content -LiteralPath $usagePath) {
        if ($line -match '(?:voice_control|voice_service)\.c:\d+:\d+:(?<name>[A-Za-z0-9_]+)\s+(?<bytes>\d+)\s+') {
            $frameBytes[$Matches.name] = [int]$Matches.bytes
        }
    }
}

$commonFrames = @(
    'voice_task',
    'execute_voice_conversation',
    'run_voice_turn'
)
$legacyFrames = @(
    'voice_service_send_turn_streaming',
    'perform_streaming_post',
    'read_http_response',
    'streaming_response_event_handler',
    'consume_streaming_data'
)
$liveCaptureFrames = @(
    'capture_user_speech',
    'publish_live_capture_upload',
    'start_live_capture_upload'
)
$liveUploadWorkerFrames = @(
    'live_capture_upload_worker',
    'voice_service_live_upload_begin',
    'voice_service_live_upload_write',
    'write_transfer_chunk',
    'write_bounded'
)
$liveFinishFrames = @(
    'voice_service_live_upload_finish',
    'read_http_response',
    'streaming_response_event_handler',
    'consume_streaming_data'
)
$playbackWorkerFrames = @(
    'streaming_playback_worker'
)
$requiredFrames = $commonFrames + $legacyFrames + $liveCaptureFrames +
    $liveUploadWorkerFrames + $liveFinishFrames + $playbackWorkerFrames |
    Select-Object -Unique
foreach ($frame in $requiredFrames) {
    if (-not $frameBytes.ContainsKey($frame)) {
        throw "Voice stack-usage report is missing required frame: $frame"
    }
}

function Measure-FramePath([string[]]$Frames) {
    return ($Frames | ForEach-Object { $frameBytes[$_] } | Measure-Object -Sum).Sum
}

$legacyPathBytes = Measure-FramePath ($commonFrames + $legacyFrames)
$liveCapturePathBytes = Measure-FramePath ($commonFrames + $liveCaptureFrames)
$liveUploadWorkerPathBytes = Measure-FramePath $liveUploadWorkerFrames
$liveFinishPathBytes = Measure-FramePath ($commonFrames + $liveFinishFrames)
$playbackWorkerPathBytes = Measure-FramePath $playbackWorkerFrames
$knownLocalPathBytes = [Math]::Max(
    $legacyPathBytes,
    [Math]::Max($liveCapturePathBytes, $liveFinishPathBytes)
)

# The local .su reports do not include esp_http_client, TCP/IP, HTTP parsing,
# authentication helpers, or their callbacks. A 12 KiB task overflowed on the
# first upload in physical testing, so retain a separate 16 KiB library reserve.
$minimumExternalHeadroomBytes = 16384
$headroomBytes = $taskStackBytes - $knownLocalPathBytes
if ($headroomBytes -lt $minimumExternalHeadroomBytes) {
    throw "Voice task stack budget is unsafe: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, headroom=$headroomBytes bytes, requiredHeadroom=$minimumExternalHeadroomBytes bytes"
}
$uploadHeadroomBytes = $uploadTaskStackBytes - $liveUploadWorkerPathBytes
if ($uploadHeadroomBytes -lt $minimumExternalHeadroomBytes) {
    throw "Voice upload task stack budget is unsafe: task=$uploadTaskStackBytes bytes, knownLocalPath=$liveUploadWorkerPathBytes bytes, headroom=$uploadHeadroomBytes bytes, requiredHeadroom=$minimumExternalHeadroomBytes bytes"
}
$playbackHeadroomBytes = $playbackTaskStackBytes - $playbackWorkerPathBytes
if ($playbackHeadroomBytes -lt $minimumExternalHeadroomBytes) {
    throw "Voice playback task stack budget is unsafe: task=$playbackTaskStackBytes bytes, knownLocalPath=$playbackWorkerPathBytes bytes, headroom=$playbackHeadroomBytes bytes, requiredHeadroom=$minimumExternalHeadroomBytes bytes"
}

Write-Output "Voice task stack budget passed: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, externalHeadroom=$headroomBytes bytes, uploadTask=$uploadTaskStackBytes bytes, uploadKnownLocalPath=$liveUploadWorkerPathBytes bytes, uploadExternalHeadroom=$uploadHeadroomBytes bytes, playbackTask=$playbackTaskStackBytes bytes, playbackKnownLocalPath=$playbackWorkerPathBytes bytes, playbackExternalHeadroom=$playbackHeadroomBytes bytes, legacyPath=$legacyPathBytes bytes, liveCapturePath=$liveCapturePathBytes bytes, liveFinishPath=$liveFinishPathBytes bytes"
