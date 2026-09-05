param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-voice-stack-analysis'
)

$ErrorActionPreference = 'Stop'

$verifier = Join-Path $PSScriptRoot 'verify-firmware-voice-stack-budget.ps1'
$voiceControlSourcePath = Join-Path $FirmwareDirectory 'main\voice_control.c'
$voiceServiceSourcePath = Join-Path $FirmwareDirectory 'main\voice_service.c'
$audioWavSourcePath = Join-Path $FirmwareDirectory 'main\audio_wav.c'
$deviceEndpointHeaderPath = Join-Path $FirmwareDirectory 'main\device_endpoint.h'
$sdkconfigDefaultsPath = Join-Path $FirmwareDirectory 'sdkconfig.defaults'
$systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = [IO.Path]::GetFullPath((Join-Path $systemTemp "stackchan-voice-stack-budget-$([Guid]::NewGuid())"))

if (-not $testRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create voice stack-budget fixture outside the system temp directory: $testRoot"
}

$voiceServiceSource = Get-Content -LiteralPath $voiceServiceSourcePath -Raw
$audioWavSource = Get-Content -LiteralPath $audioWavSourcePath -Raw
$deviceEndpointHeader = Get-Content -LiteralPath $deviceEndpointHeaderPath -Raw
$sdkconfigDefaults = Get-Content -LiteralPath $sdkconfigDefaultsPath -Raw
if ($sdkconfigDefaults -notmatch '(?m)^CONFIG_SPIRAM_TRY_ALLOCATE_WIFI_LWIP=y\r?$') {
    throw 'Wi-Fi and LwIP allocations must prefer PSRAM to preserve internal SRAM for DMA'
}
if ($voiceServiceSource.Contains('esp_http_client_set_post_field')) {
    throw 'Voice uploads must not return to the monolithic esp_http_client_set_post_field path'
}
foreach ($requiredFragment in @(
    'esp_http_client_open(client, (int)request_size)',
    'esp_http_client_open(upload->client, -1)',
    'esp_http_client_write(',
    'esp_http_client_flush_response(client, NULL)',
    'esp_wifi_set_ps(WIFI_PS_NONE)',
    'MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT',
    'log_upload_state("opened", sent, request_size, started_us)',
    '.buffer_size = VOICE_SERVICE_HTTP_BUFFER_SIZE',
    '.buffer_size_tx = VOICE_SERVICE_HTTP_BUFFER_SIZE'
)) {
    if (-not $voiceServiceSource.Contains($requiredFragment)) {
        throw "Voice upload hardening is missing required source fragment: $requiredFragment"
    }
}
if (-not $voiceServiceSource.Contains('terminal_chunk[] = "0\r\n\r\n"')) {
    throw 'Live voice uploads must terminate the HTTP chunked request body explicitly'
}
if (-not $audioWavSource.Contains('audio_wav_build_pcm16_mono_stream_header')) {
    throw 'Live voice uploads must use the bounded streaming WAV header builder'
}
if (-not $deviceEndpointHeader.Contains('/api/v1/device/voice/turn/live')) {
    throw 'The fixed same-origin live voice endpoint is missing'
}

$httpBufferMatch = [regex]::Match(
    $voiceServiceSource,
    '(?m)^#define\s+VOICE_SERVICE_HTTP_BUFFER_SIZE\s+(\d+)U?\r?$'
)
$uploadChunkMatch = [regex]::Match(
    $voiceServiceSource,
    '(?m)^#define\s+VOICE_SERVICE_UPLOAD_CHUNK_SIZE\s+(\d+)U?\r?$'
)
if (-not $httpBufferMatch.Success -or [int]$httpBufferMatch.Groups[1].Value -gt 1024) {
    throw 'Voice HTTP buffers must remain at or below 1024 bytes to protect internal SRAM'
}
if (-not $uploadChunkMatch.Success -or [int]$uploadChunkMatch.Groups[1].Value -gt 1024) {
    throw 'Voice upload chunks must remain at or below 1024 bytes to avoid starving Wi-Fi TX buffers'
}

$voiceControlSource = Get-Content -LiteralPath $voiceControlSourcePath -Raw
if (-not $voiceControlSource.Contains('heap_caps_calloc(') -or
    -not $voiceControlSource.Contains('sizeof(*stream_context), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT')) {
    throw 'Streaming turn metadata must remain in PSRAM instead of consuming the voice task stack'
}
foreach ($requiredFragment in @(
    '#define VOICE_CAPTURE_WINDOW_MS 100',
    '#define VOICE_START_CONFIRM_WINDOWS 2',
    '#define VOICE_SILENCE_WINDOWS 8',
    'xTaskCreatePinnedToCoreWithCaps(live_capture_upload_worker',
    'xTaskCreatePinnedToCoreWithCaps(streaming_playback_worker',
    'xQueueCreate(',
    'VOICE_PLAYBACK_QUEUE_DEPTH',
    '*retain_payload = true',
    'MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT',
    'voice_service_live_upload_mark_capture_finished_at(',
    'publish_live_capture_upload(live_upload, *captured_samples, true)'
)) {
    if (-not $voiceControlSource.Contains($requiredFragment)) {
        throw "Live capture/upload overlap is missing required source fragment: $requiredFragment"
    }
}

function Set-FixtureTaskStacks([int]$VoiceBytes, [int]$UploadBytes, [int]$PlaybackBytes) {
    $fixtureSource = Join-Path $testRoot 'main\voice_control.c'
    $source = Get-Content -LiteralPath $fixtureSource -Raw
    $updated = [regex]::Replace(
        $source,
        '(?m)^#define\s+VOICE_TASK_STACK_SIZE\s+\d+\r?$',
        "#define VOICE_TASK_STACK_SIZE $VoiceBytes"
    )
    $updated = [regex]::Replace(
        $updated,
        '(?m)^#define\s+VOICE_UPLOAD_TASK_STACK_SIZE\s+\d+\r?$',
        "#define VOICE_UPLOAD_TASK_STACK_SIZE $UploadBytes"
    )
    $updated = [regex]::Replace(
        $updated,
        '(?m)^#define\s+VOICE_PLAYBACK_TASK_STACK_SIZE\s+\d+\r?$',
        "#define VOICE_PLAYBACK_TASK_STACK_SIZE $PlaybackBytes"
    )
    if ($updated -eq $source) {
        throw 'Fixture voice task stack sizes were not replaced'
    }
    [IO.File]::WriteAllText($fixtureSource, $updated, [Text.UTF8Encoding]::new($false))
}

try {
    $usageDirectory = Join-Path $testRoot "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir"
    New-Item -ItemType Directory -Path (Join-Path $testRoot 'main') -Force | Out-Null
    New-Item -ItemType Directory -Path $usageDirectory -Force | Out-Null
    Copy-Item -LiteralPath $voiceControlSourcePath -Destination (Join-Path $testRoot 'main\voice_control.c')
    Copy-Item -LiteralPath $voiceServiceSourcePath -Destination (Join-Path $testRoot 'main\voice_service.c')
    [IO.File]::WriteAllLines(
        (Join-Path $usageDirectory 'voice_control.c.su'),
        @(
            'voice_control.c:150:1:capture_user_speech 320 static'
            'voice_control.c:165:1:publish_live_capture_upload 96 static'
            'voice_control.c:175:1:start_live_capture_upload 160 static'
            'voice_control.c:190:1:live_capture_upload_worker 512 static'
            'voice_control.c:235:1:streaming_playback_worker 640 static'
            'voice_control.c:188:1:run_voice_turn 768 static'
            'voice_control.c:320:1:execute_voice_conversation 256 static'
            'voice_control.c:390:1:voice_task 768 static'
        ),
        [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllLines(
        (Join-Path $usageDirectory 'voice_service.c.su'),
        @(
            'voice_service.c:190:1:consume_streaming_data 256 static'
            'voice_service.c:243:1:streaming_response_event_handler 128 static'
            'voice_service.c:274:1:read_http_response 768 static'
            'voice_service.c:298:1:perform_streaming_post 512 static'
            'voice_service.c:457:1:voice_service_send_turn_streaming 1024 static'
            'voice_service.c:523:1:voice_service_live_upload_begin 640 static'
            'voice_service.c:630:1:voice_service_live_upload_write 128 static'
            'voice_service.c:653:1:voice_service_live_upload_finish 512 static'
            'voice_service.c:322:1:write_transfer_chunk 128 static'
            'voice_service.c:298:1:write_bounded 96 static'
        ),
        [Text.UTF8Encoding]::new($false)
    )

    Set-FixtureTaskStacks 12288 24576 24576
    $unsafeFailed = $false
    $unsafeMessage = ''
    try {
        & $verifier -FirmwareDirectory $testRoot -BuildDirectory $BuildDirectory | Out-Null
    }
    catch {
        $unsafeFailed = $true
        $unsafeMessage = $_.Exception.Message
    }
    if (-not $unsafeFailed) {
        throw 'The unsafe 12288-byte voice task budget unexpectedly passed'
    }
    if ($unsafeMessage -notmatch 'requiredHeadroom=16384 bytes') {
        throw "The unsafe voice budget failed without the required 16384-byte external-call reserve: $unsafeMessage"
    }

    Set-FixtureTaskStacks 32768 8192 24576
    $unsafeUploadFailed = $false
    $unsafeUploadMessage = ''
    try {
        & $verifier -FirmwareDirectory $testRoot -BuildDirectory $BuildDirectory | Out-Null
    }
    catch {
        $unsafeUploadFailed = $true
        $unsafeUploadMessage = $_.Exception.Message
    }
    if (-not $unsafeUploadFailed) {
        throw 'The unsafe 8192-byte upload task budget unexpectedly passed'
    }
    if ($unsafeUploadMessage -notmatch 'Voice upload task stack budget is unsafe') {
        throw "The unsafe upload budget failed for an unexpected reason: $unsafeUploadMessage"
    }

    Set-FixtureTaskStacks 32768 24576 8192
    $unsafePlaybackFailed = $false
    $unsafePlaybackMessage = ''
    try {
        & $verifier -FirmwareDirectory $testRoot -BuildDirectory $BuildDirectory | Out-Null
    }
    catch {
        $unsafePlaybackFailed = $true
        $unsafePlaybackMessage = $_.Exception.Message
    }
    if (-not $unsafePlaybackFailed) {
        throw 'The unsafe 8192-byte playback task budget unexpectedly passed'
    }
    if ($unsafePlaybackMessage -notmatch 'Voice playback task stack budget is unsafe') {
        throw "The unsafe playback budget failed for an unexpected reason: $unsafePlaybackMessage"
    }

    Set-FixtureTaskStacks 32768 24576 24576
    & $verifier -FirmwareDirectory $testRoot -BuildDirectory $BuildDirectory | Out-Null

    Write-Output 'Voice stack-budget regression passed: 12288-byte voice and 8192-byte upload/playback tasks rejected; 32768/24576/24576 accepted'
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
