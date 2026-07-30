param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-voice-stack-analysis'
)

$ErrorActionPreference = 'Stop'

$verifier = Join-Path $PSScriptRoot 'verify-firmware-voice-stack-budget.ps1'
$voiceControlSourcePath = Join-Path $FirmwareDirectory 'main\voice_control.c'
$voiceServiceSourcePath = Join-Path $FirmwareDirectory 'main\voice_service.c'
$systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = [IO.Path]::GetFullPath((Join-Path $systemTemp "stackchan-voice-stack-budget-$([Guid]::NewGuid())"))

if (-not $testRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create voice stack-budget fixture outside the system temp directory: $testRoot"
}

function Set-FixtureTaskStack([int]$Bytes) {
    $fixtureSource = Join-Path $testRoot 'main\voice_control.c'
    $source = Get-Content -LiteralPath $fixtureSource -Raw
    $updated = [regex]::Replace(
        $source,
        '(?m)^#define\s+VOICE_TASK_STACK_SIZE\s+\d+\r?$',
        "#define VOICE_TASK_STACK_SIZE $Bytes"
    )
    if ($updated -eq $source) {
        throw 'Fixture voice task stack size was not replaced'
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
            'voice_control.c:188:1:run_voice_turn 768 static'
            'voice_control.c:320:1:execute_voice_conversation 256 static'
            'voice_control.c:390:1:voice_task 768 static'
        ),
        [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllLines(
        (Join-Path $usageDirectory 'voice_service.c.su'),
        @(
            'voice_service.c:49:1:response_event_handler 512 static'
            'voice_service.c:69:1:perform_request 768 static'
            'voice_service.c:139:1:voice_service_send_turn 512 static'
        ),
        [Text.UTF8Encoding]::new($false)
    )

    Set-FixtureTaskStack 12288
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

    Set-FixtureTaskStack 32768
    & $verifier -FirmwareDirectory $testRoot -BuildDirectory $BuildDirectory | Out-Null

    Write-Output 'Voice stack-budget regression passed: 12288 rejected and 32768 accepted'
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
