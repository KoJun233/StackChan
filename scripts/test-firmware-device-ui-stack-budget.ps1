param([string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'))

$ErrorActionPreference = 'Stop'
$verifier = Join-Path $PSScriptRoot 'verify-firmware-device-ui-stack-budget.ps1'
$buildDirectory = 'build-stack-fixture'
$systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$testRoot = [IO.Path]::GetFullPath((Join-Path $systemTemp "stackchan-device-ui-stack-$([Guid]::NewGuid())"))
if (-not $testRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create stack fixture outside the system temp directory: $testRoot"
}
$usageDirectory = Join-Path $testRoot "$buildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir"
$encoding = [Text.UTF8Encoding]::new($false)
$originalSources = @{}
$fixtures = @{
    'device_ui.cpp' = @(
        @('void {anonymous}::worker(void*)', 848),
        @('void {anonymous}::flash_worker(void*)', 96),
        @('int {anonymous}::flash_call(FlashAction, int, device_identity_t*)', 48),
        @('void {anonymous}::tracking_gate_worker(void*)', 112),
        @('bool device_ui_local_interaction_allowed()', 32)
    )
    'device_ui_protocol.c' = @(
        @('device_ui_parse_state', 80), @('device_ui_parse_card', 64),
        @('device_ui_parse_shown_receipt', 96), @('parse', 48), @('unique_objects', 32)
    )
    'face_tracking.cpp' = @(
        @('void tracking_task(void*)', 544), @('bool same_capture_gate(uint32_t)', 48),
        @('bool gate_snapshot(uint32_t*, bool*)', 32), @('bool release_session(camera_session_t*)', 48)
    )
    'face_tracking_policy.c' = @(@('face_tracking_policy_update', 80), @('clamp', 32))
    'voice_control.c' = @(
        @('voice_touch_task', 112), @('online_identity_available$part$0', 1968),
        @('voice_control_motion_blocked', 32), @('voice_control_set_input_mode', 96)
    )
    'device_identity.c' = @(
        @('device_identity_load', 48), @('get_required_string', 48),
        @('device_identity_is_valid', 48), @('device_identity_is_valid_server_base_url', 32),
        @('is_valid_lan_http_server_base_url', 48)
    )
    'body_hardware.cpp' = @(
        @('bool body_hardware_follow_local(float, float, bool)', 48),
        @('bool queue_local_motion(bool, const body_local_goal_t*)', 208),
        @('bool {anonymous}::local_interaction_guard(safety_motion_guard_t*, bool)', 32)
    )
    'companion_hardware.cpp' = @(
        @('void companion_hardware_get_motion_guard(bool*, bool*, bool*)', 32),
        @('bool companion_hardware_is_idle()', 32)
    )
    'safety_state.c' = @(@('safety_state_get_diagnostics', 32), @('safety_state_begin_motion', 32))
}

function Reset-Fixture {
    foreach ($file in $fixtures.Keys) {
        [IO.File]::WriteAllText((Join-Path $testRoot "main\$file"), $originalSources[$file], $encoding)
        $lines = foreach ($frame in $fixtures[$file]) { "/fixture/main/${file}:1:1:$($frame[0])`t$($frame[1])`tstatic" }
        [IO.File]::WriteAllLines((Join-Path $usageDirectory "$file.su"), [string[]]$lines, $encoding)
    }
}
function Rewrite-Source([string]$File, [string]$Pattern, [string]$Replacement) {
    $path = Join-Path $testRoot "main\$File"
    $source = Get-Content -LiteralPath $path -Raw
    $updated = [regex]::Replace($source, $Pattern, $Replacement)
    if ($updated -eq $source) { throw "Fixture source mutation did not match: $File" }
    [IO.File]::WriteAllText($path, $updated, $encoding)
}
function Reject-Fixture([string]$Name, [string]$Expected, [scriptblock]$Mutation) {
    Reset-Fixture
    & $Mutation
    $message = ''
    try { & $verifier -FirmwareDirectory $testRoot -BuildDirectory $buildDirectory | Out-Null }
    catch { $message = $_.Exception.Message }
    if ($message -notmatch $Expected) { throw "$Name did not fail with the expected check ($Expected): $message" }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $testRoot 'main') -Force | Out-Null
    New-Item -ItemType Directory -Path $usageDirectory -Force | Out-Null
    foreach ($file in $fixtures.Keys) { $originalSources[$file] = Get-Content -LiteralPath (Join-Path $FirmwareDirectory "main\$file") -Raw }
    Reset-Fixture
    $output = & $verifier -FirmwareDirectory $testRoot -BuildDirectory $buildDirectory
    if (($output -join '\n') -notmatch 'knownLocalPath=2256 bytes' -or ($output -join '\n') -notmatch 'remain unmeasured') {
        throw 'Safe fixture must include the optimized identity-check frame and state the analysis limit'
    }
    Reject-Fixture 'Reduced HTTP stack' 'UI HTTP task stack budget is unsafe' {
        Rewrite-Source 'device_ui.cpp' '("device_ui"\s*,\s*)\d+' '${1}16384'
    }
    Reject-Fixture 'Reduced gate stack' 'Local gate task stack budget is unsafe' {
        Rewrite-Source 'device_ui.cpp' '("local_ui_gate"\s*,\s*)\d+' '${1}4096'
    }
    Reject-Fixture 'Reduced vision stack' 'Vision task stack budget is unsafe' {
        Rewrite-Source 'face_tracking.cpp' '(#define\s+FACE_TRACKING_TASK_STACK_SIZE\s+)\d+' '${1}8192'
    }
    Reject-Fixture 'Reduced touch stack' 'Touch task stack budget is unsafe' {
        Rewrite-Source 'voice_control.c' '(#define\s+VOICE_TOUCH_TASK_STACK_SIZE\s+)\d+' '${1}3072'
    }
    Reject-Fixture 'Missing task frame' 'missing required frame: device_ui.cpp/worker' {
        $path = Join-Path $usageDirectory 'device_ui.cpp.su'
        [IO.File]::WriteAllLines($path, [string[]]((Get-Content -LiteralPath $path) | Where-Object { $_ -notmatch '::worker\(' }), $encoding)
    }
    Reject-Fixture 'Dynamic frame' 'unbounded/unverified frame' {
        $path = Join-Path $usageDirectory 'face_tracking.cpp.su'
        [IO.File]::WriteAllText($path, (Get-Content -LiteralPath $path -Raw).Replace("`tstatic", "`tdynamic"), $encoding)
    }
    Reject-Fixture 'HTTP memory allocation changed' 'explicit numeric PSRAM task stack' {
        Rewrite-Source 'device_ui.cpp' 'MALLOC_CAP_SPIRAM\s*\|\s*MALLOC_CAP_8BIT' 'MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT'
    }
    Reject-Fixture 'Flash worker changed to external allocation' 'internal ui_flash worker' {
        Rewrite-Source 'device_ui.cpp' 'xTaskCreate\(flash_worker, "ui_flash", 8192' 'xTaskCreatePinnedToCoreWithCaps(flash_worker, "ui_flash", 8192'
    }
    Reject-Fixture 'JSON recursive guard removed' 'explicit bounded nesting limit' {
        Rewrite-Source 'device_ui_protocol.c' '(\+\+depth\s*>\s*)\d+' '${1}17'
    }
    Write-Output 'Device UI stack-budget regression passed: safe C/C++ and optimized clone frames accepted; four reduced stacks, missing/dynamic frames, changed PSRAM allocation and unbounded JSON recursion rejected; external Flash allocation rejected (10 scenarios).'
}
finally {
    $resolvedTarget = [IO.Path]::GetFullPath($testRoot)
    if (-not $resolvedTarget.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove stack fixture outside the verified temp directory: $resolvedTarget"
    }
    if (Test-Path -LiteralPath $resolvedTarget) { Remove-Item -LiteralPath $resolvedTarget -Recurse -Force }
}
