param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-device008-vision-resolve'
)

$ErrorActionPreference = 'Stop'
$usageDirectory = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir"
$sources = @{}
$frames = @{}
foreach ($file in @('device_ui.cpp', 'device_ui_protocol.c', 'face_tracking.cpp',
        'face_tracking_policy.c', 'voice_control.c', 'device_identity.c',
        'body_hardware.cpp', 'companion_hardware.cpp', 'safety_state.c')) {
    $sourcePath = Join-Path $FirmwareDirectory "main\$file"
    $usagePath = Join-Path $usageDirectory "$file.su"
    foreach ($path in @($sourcePath, $usagePath)) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Device UI stack-budget input was not found: $path. Build with -fstack-usage first."
        }
    }
    $sources[$file] = Get-Content -LiteralPath $sourcePath -Raw
    $pattern = '^(?:.*[/\\])?' + [regex]::Escape($file) + ':\d+:\d+:(?<signature>.+?)\t(?<bytes>\d+)\t(?<kind>.+)$'
    foreach ($line in Get-Content -LiteralPath $usagePath) {
        $match = [regex]::Match($line, $pattern)
        if (-not $match.Success) { continue }
        $signature = $match.Groups['signature'].Value
        # GCC C++ reports demangled signatures; C clone names use .constprop or $part$0.
        $nameMatch = if ($signature.Contains('(')) {
            [regex]::Match($signature, '(?<name>[A-Za-z_][A-Za-z0-9_.$]*)(?=\()')
        } else { [regex]::Match($signature, '^(?<name>[A-Za-z_][A-Za-z0-9_.$]*)$') }
        if (-not $nameMatch.Success) { continue }
        $name = [regex]::Replace($nameMatch.Groups['name'].Value, '[.$].*$', '')
        $key = "$file/$name"
        $bytes = [int]$match.Groups['bytes'].Value
        $kind = $match.Groups['kind'].Value.Trim()
        if ($kind -ne 'static') {
            throw "Device UI stack-usage report contains an unbounded/unverified frame: $key ($kind)"
        }
        if (-not $frames.ContainsKey($key) -or $bytes -gt $frames[$key]) { $frames[$key] = $bytes }
    }
}

function Frame([string]$Key, [switch]$Optional) {
    if ($frames.ContainsKey($Key)) { return $frames[$Key] }
    # -Os can fold these explicitly named helpers into their caller's reported frame.
    if ($Optional) { return 0 }
    throw "Device UI stack-usage report is missing required frame: $Key"
}
function Maximum([int[]]$Values) { return ($Values | Measure-Object -Maximum).Maximum }
function DefinedStack([string]$File, [string]$Macro) {
    $match = [regex]::Match($sources[$File], '(?m)^#define\s+' + $Macro + '\s+(\d+)\r?$')
    if (-not $match.Success) { throw "$Macro must be an explicit numeric value" }
    return [int]$match.Groups[1].Value
}
function PsramStack([string]$Entry, [string]$Name) {
    $pattern = 'xTaskCreatePinnedToCoreWithCaps\(\s*' + $Entry + '\s*,\s*"' + $Name +
        '"\s*,\s*(\d+)\s*,[^;]+?MALLOC_CAP_SPIRAM\s*\|\s*MALLOC_CAP_8BIT\s*\)'
    $match = [regex]::Match($sources['device_ui.cpp'], $pattern)
    if (-not $match.Success) { throw "$Name must have an explicit numeric PSRAM task stack" }
    return [int]$match.Groups[1].Value
}

$httpStack = PsramStack 'worker' 'device_ui'
$gateStack = PsramStack 'tracking_gate_worker' 'local_ui_gate'
$visionStack = DefinedStack 'face_tracking.cpp' 'FACE_TRACKING_TASK_STACK_SIZE'
$touchStack = DefinedStack 'voice_control.c' 'VOICE_TOUCH_TASK_STACK_SIZE'
if ($sources['face_tracking.cpp'] -notmatch 'xTaskCreate\(\s*tracking_task\s*,\s*"face_tracking"\s*,\s*FACE_TRACKING_TASK_STACK_SIZE' -or
    $sources['voice_control.c'] -notmatch 'xTaskCreate\(\s*voice_touch_task\s*,\s*"voice_touch"\s*,\s*VOICE_TOUCH_TASK_STACK_SIZE') {
    throw 'Vision and touch must retain the default internal xTaskCreate allocation'
}

$depthMatch = [regex]::Match($sources['device_ui_protocol.c'], '\+\+depth\s*>\s*(\d+)')
if (-not $depthMatch.Success -or [int]$depthMatch.Groups[1].Value -gt 16) {
    throw 'UI JSON parsing requires an explicit bounded nesting limit no greater than 16'
}
# unique_objects also visits the scalar leaf beneath the deepest container.
$jsonFrames = [int]$depthMatch.Groups[1].Value + 1
$identityValidation = (Frame 'device_identity.c/device_identity_is_valid') + (Maximum @(
    ((Frame 'device_identity.c/device_identity_is_valid_server_base_url') + (Maximum @(
        (Frame 'device_identity.c/is_valid_lan_http_server_base_url' -Optional),
        (Frame 'device_identity.c/is_valid_https_server_base_url' -Optional)))),
    (Frame 'device_identity.c/is_valid_token' -Optional),
    (Frame 'device_identity.c/is_decimal_at' -Optional),
    (Frame 'device_identity.c/parse_decimal' -Optional)))
$identityLoad = (Frame 'device_identity.c/device_identity_load') + (Maximum @(
    (Frame 'device_identity.c/get_required_string'), $identityValidation))
$uiParse = (Maximum @((Frame 'device_ui_protocol.c/device_ui_parse_state'),
    (Frame 'device_ui_protocol.c/device_ui_parse_card'), (Frame 'device_ui_protocol.c/device_ui_parse_shown_receipt'))) +
    (Frame 'device_ui_protocol.c/parse') + (Frame 'device_ui_protocol.c/unique_objects') * $jsonFrames
if ($sources['device_ui.cpp'] -notmatch 'xTaskCreate\(\s*flash_worker\s*,\s*"ui_flash"\s*,\s*8192' -or
    $sources['device_ui.cpp'] -notmatch 'flash_call\(LOAD_IDENTITY' -or
    $sources['device_ui.cpp'] -notmatch 'flash_call\(SAVE_INPUT_MODE') {
    throw 'UI NVS identity reads and mode writes require the internal ui_flash worker'
}
$flashPath = (Frame 'device_ui.cpp/flash_worker') + (Maximum @(
    $identityLoad, (Frame 'voice_control.c/voice_control_set_input_mode')))
$httpPath = (Frame 'device_ui.cpp/worker') + (Maximum @(
    ((Frame 'device_ui.cpp/http' -Optional) + (Frame 'device_ui.cpp/flash_call' -Optional)), $uiParse))
$gatePath = (Frame 'device_ui.cpp/tracking_gate_worker') + (Frame 'device_ui.cpp/service_tracking' -Optional) +
    (Frame 'body_hardware.cpp/body_hardware_follow_local') + (Frame 'body_hardware.cpp/queue_local_motion') +
    (Frame 'body_hardware.cpp/local_interaction_guard') + (Maximum @(
        (Frame 'voice_control.c/voice_control_motion_blocked'),
        (Frame 'companion_hardware.cpp/companion_hardware_get_motion_guard'),
        (Frame 'companion_hardware.cpp/companion_hardware_is_idle'),
        (Frame 'device_ui.cpp/device_ui_local_interaction_allowed')))
$visionPath = (Frame 'face_tracking.cpp/tracking_task') + (Maximum @(
    ((Frame 'face_tracking.cpp/start_session' -Optional) + (Frame 'face_tracking.cpp/same_capture_gate') + (Frame 'face_tracking.cpp/gate_snapshot')),
    ((Frame 'face_tracking.cpp/sample_session' -Optional) + (Frame 'face_tracking_policy.c/face_tracking_policy_update') + (Frame 'face_tracking_policy.c/clamp' -Optional)),
    (Frame 'face_tracking.cpp/release_session')))
$touchPath = (Frame 'voice_control.c/voice_touch_task') +
    (Frame 'voice_control.c/online_identity_available') + $identityLoad

# These are selected project-owned call paths, not a complete call-graph bound.
# Inlined helpers are already included by GCC in their emitted caller's frame.
# HTTP/TLS, NVS/RTOS, video/ESP-DL and virtual inference calls remain independent
# runtime reserves. No physical high-water measurement is claimed by this check.
foreach ($budget in @(
    @{ Name = 'UI Flash'; Stack = 8192; Path = $flashPath; Reserve = 4096; Memory = 'internal' },
    @{ Name = 'UI HTTP'; Stack = $httpStack; Path = $httpPath; Reserve = 16384; Memory = 'PSRAM' },
    @{ Name = 'Local gate'; Stack = $gateStack; Path = $gatePath; Reserve = 4096; Memory = 'PSRAM' },
    @{ Name = 'Vision'; Stack = $visionStack; Path = $visionPath; Reserve = 8192; Memory = 'internal' },
    @{ Name = 'Touch'; Stack = $touchStack; Path = $touchPath; Reserve = 1536; Memory = 'internal' }
)) {
    $headroom = $budget.Stack - $budget.Path
    if ($headroom -lt $budget.Reserve) {
        throw "$($budget.Name) task stack budget is unsafe: task=$($budget.Stack) bytes, knownLocalPath=$($budget.Path) bytes, headroom=$headroom bytes, requiredHeadroom=$($budget.Reserve) bytes"
    }
    Write-Output "$($budget.Name) static stack budget passed: task=$($budget.Stack) bytes ($($budget.Memory)), knownLocalPath=$($budget.Path) bytes, runtimeHeadroom=$headroom bytes, requiredHeadroom=$($budget.Reserve) bytes"
}
Write-Output 'Scope: selected local paths only; third-party call depth and physical task high-water marks remain unmeasured.'
