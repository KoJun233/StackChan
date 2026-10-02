param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-stack-analysis'
)

$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $FirmwareDirectory 'main\device_provisioning.c'
$usagePath = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir\device_provisioning.c.su"

if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Provisioning source was not found: $sourcePath"
}
if (-not (Test-Path -LiteralPath $usagePath)) {
    throw "Stack-usage report was not found: $usagePath. Build with -fstack-usage first."
}

$source = Get-Content -LiteralPath $sourcePath -Raw
$taskStackMatch = [regex]::Match($source, '(?m)^#define\s+PROVISIONING_TASK_STACK_SIZE\s+(\d+)\r?$')
if (-not $taskStackMatch.Success) {
    throw 'PROVISIONING_TASK_STACK_SIZE must be an explicit numeric value'
}
$taskStackBytes = [int]$taskStackMatch.Groups[1].Value

$frameBytes = @{}
$usagePaths = @($usagePath)
if ($source.Contains('relocate_server(const')) {
    $credentialUsage = Join-Path $FirmwareDirectory "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir\device_credentials.c.su"
    if (-not (Test-Path -LiteralPath $credentialUsage)) { throw "Credential stack-usage report was not found: $credentialUsage" }
    $usagePaths += $credentialUsage
}
foreach ($line in Get-Content -LiteralPath $usagePaths) {
    if ($line -match '(?:device_provisioning|device_credentials)\.c:\d+:\d+:(?<name>[A-Za-z0-9_.$]+)\s+(?<bytes>\d+)\s+') {
        # -Os emits .constprop/$part clones. Use their largest frame.
        $frameName = [regex]::Replace($Matches.name, '[.$].*$', '')
        $bytes = [int]$Matches.bytes
        if (-not $frameBytes.ContainsKey($frameName) -or $bytes -gt $frameBytes[$frameName]) {
            $frameBytes[$frameName] = $bytes
        }
    }
}

$requiredFrames = @('provisioning_task', 'provision', 'claim_device', 'device_provisioning_parse_claim_response')
foreach ($frame in $requiredFrames) {
    if (-not $frameBytes.ContainsKey($frame)) {
        throw "Stack-usage report is missing required frame: $frame"
    }
}

$knownLocalPathBytes = ($requiredFrames | ForEach-Object { $frameBytes[$_] } | Measure-Object -Sum).Sum
if ($usagePaths.Count -gt 1) {
    $relocationFrames = @('provisioning_task', 'relocate_server', 'device_credentials_refresh', 'device_credentials_parse_refresh_response')
    foreach ($frame in $relocationFrames) {
        if (-not $frameBytes.ContainsKey($frame)) { throw "Stack-usage report is missing required frame: $frame" }
    }
    $relocationBytes = ($relocationFrames | ForEach-Object { $frameBytes[$_] } | Measure-Object -Sum).Sum
    $knownLocalPathBytes = [math]::Max($knownLocalPathBytes, $relocationBytes)
}


# The .su report proves the local provisioning frames. Calls into cJSON,
# esp_http_client, NVS, TLS/TCP, and their callbacks are outside that local
# sum, so reserve a separate full 8 KiB for external/library call depth.
$minimumExternalHeadroomBytes = 8192
$headroomBytes = $taskStackBytes - $knownLocalPathBytes
if ($headroomBytes -lt $minimumExternalHeadroomBytes) {
    throw "Provisioning task stack budget is unsafe: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, headroom=$headroomBytes bytes, requiredHeadroom=$minimumExternalHeadroomBytes bytes"
}

Write-Output "Provisioning task stack budget passed: task=$taskStackBytes bytes, knownLocalPath=$knownLocalPathBytes bytes, externalHeadroom=$headroomBytes bytes"
