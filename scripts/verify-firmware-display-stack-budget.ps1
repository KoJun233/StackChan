param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-device008-lan'
)
$ErrorActionPreference = 'Stop'
$buildRoot = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf"
$wanted = @('device_ui.cpp', 'companion_hardware.cpp', 'robot_eyes_renderer.c',
    'lv_event.c', 'lv_obj_event.c', 'lv_obj_tree.c', 'lv_obj_class.c', 'lv_label.c',
    'lv_refr.c', 'lv_obj_draw.c', 'esp_lvgl_port.c')
$frames = @{}
$reports = @{}
foreach ($path in (& rg --files --no-ignore $buildRoot | Where-Object { $_.EndsWith('.su') })) {
    $file = [IO.Path]::GetFileName($path) -replace '\.su$', ''
    if ($file -notin $wanted) { continue }
    $reports[$file] = $true
    foreach ($line in Get-Content -LiteralPath $path) {
        $match = [regex]::Match($line, '^.+:\d+:\d+:(?<signature>[^\t]+)\t(?<bytes>\d+)\t(?<kind>.+)$')
        if (-not $match.Success) { continue }
        $signature = $match.Groups['signature'].Value
        $name = if ($signature.Contains('(')) {
            [regex]::Match($signature, '(?<name>[A-Za-z_][A-Za-z0-9_.$]*)(?=\()').Groups['name'].Value
        } else { $signature }
        $name = [regex]::Replace($name, '[.$].*$', '')
        $key = "$file/$name"
        if ($match.Groups['kind'].Value.Trim() -ne 'static') {
            throw "Display stack report contains an unbounded/unverified frame: $key"
        }
        $bytes = [int]$match.Groups['bytes'].Value
        if (-not $frames.ContainsKey($key) -or $frames[$key] -lt $bytes) { $frames[$key] = $bytes }
    }
}
foreach ($file in $wanted) {
    if (-not $reports.ContainsKey($file)) { throw "Missing actual display stack report: $file. Build with STACKCHAN_STACK_USAGE=ON." }
}
function Frame([string]$Key, [switch]$Optional) {
    if ($frames.ContainsKey($Key)) { return $frames[$Key] }
    if ($Optional) { return 0 } # Inlined helpers are included in their callers.
    throw "Missing required display frame: $Key"
}
function Maximum([int[]]$Values) { return ($Values | Measure-Object -Maximum).Maximum }
$source = Get-Content -LiteralPath (Join-Path $FirmwareDirectory 'main/companion_hardware.cpp') -Raw
$uiMatch = [regex]::Match($source, '(?m)^#define UI_TASK_STACK_SIZE (\d+)')
$lvglMatch = [regex]::Match($source, 'display_config\.lvgl_port_cfg\.task_stack\s*=\s*(\d+)')
if (-not $uiMatch.Success -or -not $lvglMatch.Success) { throw 'Display task stacks must remain explicit numeric values' }
$uiStack = [int]$uiMatch.Groups[1].Value
$lvglStack = [int]$lvglMatch.Groups[1].Value
# Native menu/card fixtures assert a maximum object depth of six. This is a
# selected path estimate for those views, not a bound for arbitrary media packs.
$depth = 6
$event = (Frame 'lv_event.c/lv_event_send') + (Frame 'lv_obj_event.c/lv_obj_send_event') + (Frame 'lv_obj_event.c/event_send_core')
$label = (Frame 'device_ui.cpp/label') + (Frame 'lv_label.c/lv_label_set_text') + (Frame 'lv_label.c/lv_label_event')
$create = (Frame 'device_ui.cpp/draw_menu' -Optional) + (Frame 'device_ui.cpp/positioned_button' -Optional) +
    (Frame 'device_ui.cpp/button') + $label + (Frame 'lv_obj_class.c/lv_obj_class_create_obj') +
    (Frame 'lv_obj_class.c/lv_obj_class_init_obj') + $event * $depth
$delete = (Frame 'lv_obj_tree.c/lv_obj_clean') + (Frame 'lv_obj_tree.c/lv_obj_delete') +
    ((Frame 'lv_obj_tree.c/obj_delete_core') + $event) * $depth
$scene = (Frame 'companion_hardware.cpp/ui_task') + (Frame 'companion_hardware.cpp/sample_touch' -Optional) +
    (Frame 'device_ui.cpp/device_ui_tick_locked') + (Frame 'device_ui.cpp/render_locked' -Optional) +
    (Maximum @($create, $delete, (Frame 'device_ui.cpp/refresh_menu')))
$refresh = (Frame 'lv_refr.c/lv_refr_now') + (Frame 'lv_refr.c/lv_display_refr_timer') +
    (Frame 'lv_refr.c/refr_area') + (Frame 'lv_refr.c/refr_obj_and_children') * $depth +
    (Frame 'lv_refr.c/lv_obj_redraw') + $event + (Maximum @(
        ((Frame 'robot_eyes_renderer.c/draw_face') +
            (Maximum @((Frame 'robot_eyes_renderer.c/draw_eye' -Optional),
                (Frame 'robot_eyes_renderer.c/draw_status' -Optional))) +
            (Frame 'robot_eyes_renderer.c/rectangle' -Optional)),
        ((Maximum @((Frame 'device_ui.cpp/icon_draw'),
            (Frame 'device_ui.cpp/menu_header_draw' -Optional))) +
            (Maximum @((Frame 'device_ui.cpp/icon_rect'),
                (Frame 'device_ui.cpp/icon_arc' -Optional), (Frame 'device_ui.cpp/icon_line' -Optional)))),
        (Frame 'lv_label.c/lv_label_event')))
$manual = (Frame 'companion_hardware.cpp/ui_task') + (Frame 'companion_hardware.cpp/draw_builtin_face_locked') + $refresh
$events = $event * $depth + (Frame 'device_ui.cpp/slider_event') + (Frame 'device_ui.cpp/clicked') +
    (Maximum @((Frame 'device_ui.cpp/enqueue'), $label))
$port = (Frame 'esp_lvgl_port.c/lvgl_port_task') + (Maximum @($refresh, $events))
foreach ($budget in @(
    @{Name='UI scene'; Stack=$uiStack; Path=$scene; Reserve=3072},
    @{Name='UI refresh'; Stack=$uiStack; Path=$manual; Reserve=3072},
    @{Name='LVGL draw/events'; Stack=$lvglStack; Path=$port; Reserve=4096}
)) {
    $headroom = $budget.Stack - $budget.Path
    if ($headroom -lt $budget.Reserve) { throw "$($budget.Name) display stack budget is unsafe: task=$($budget.Stack), selectedFrames=$($budget.Path), reserve=$($budget.Reserve)" }
    Write-Output "$($budget.Name) display stack budget passed: task=$($budget.Stack), selectedFrames=$($budget.Path), headroom=$headroom, reserve=$($budget.Reserve)"
}
Write-Output 'Scope: native modal depth <=6 and selected actual project/LVGL frames; other library/RTOS/ISR depths and physical task high-water remain unmeasured.'
