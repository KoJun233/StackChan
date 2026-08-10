param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-transport-stack-analysis'
)

$ErrorActionPreference = 'Stop'

$verifier = Join-Path $PSScriptRoot 'verify-firmware-transport-stack-budget.ps1'
$transportSourcePath = Join-Path $FirmwareDirectory 'main\device_transport.c'
$firmwareOtaSourcePath = Join-Path $FirmwareDirectory 'main\firmware_ota.c'
$systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = [IO.Path]::GetFullPath((Join-Path $systemTemp "stackchan-transport-stack-budget-$([Guid]::NewGuid())"))

if (-not $testRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create transport stack-budget fixture outside the system temp directory: $testRoot"
}

function Set-FixtureTaskStack([int]$Bytes) {
    $fixtureSource = Join-Path $testRoot 'main\device_transport.c'
    $source = Get-Content -LiteralPath $fixtureSource -Raw
    $updated = [regex]::Replace(
        $source,
        '(?m)^#define\s+TRANSPORT_TASK_STACK_SIZE\s+\d+\r?$',
        "#define TRANSPORT_TASK_STACK_SIZE $Bytes"
    )
    if ($updated -eq $source) {
        throw 'Fixture transport task stack size was not replaced'
    }
    [IO.File]::WriteAllText($fixtureSource, $updated, [Text.UTF8Encoding]::new($false))
}

try {
    $usageDirectory = Join-Path $testRoot "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir"
    New-Item -ItemType Directory -Path (Join-Path $testRoot 'main') -Force | Out-Null
    New-Item -ItemType Directory -Path $usageDirectory -Force | Out-Null
    Copy-Item -LiteralPath $transportSourcePath -Destination (Join-Path $testRoot 'main\device_transport.c')
    Copy-Item -LiteralPath $firmwareOtaSourcePath -Destination (Join-Path $testRoot 'main\firmware_ota.c')
    [IO.File]::WriteAllLines(
        (Join-Path $usageDirectory 'device_transport.c.su'),
        @(
            'device_transport.c:585:1:run_websocket_connection 3072 static'
            'device_transport.c:806:1:transport_task 1024 static'
        ),
        [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllLines(
        (Join-Path $usageDirectory 'firmware_ota.c.su'),
        @(
            'firmware_ota.c:260:1:download_image 1536 static'
            'firmware_ota.c:357:1:firmware_ota_install 512 static'
        ),
        [Text.UTF8Encoding]::new($false)
    )

    Set-FixtureTaskStack 10240
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
        throw 'The unsafe 10240-byte transport task budget unexpectedly passed'
    }
    if ($unsafeMessage -notmatch 'requiredHeadroom=16384 bytes') {
        throw "The unsafe transport budget failed without the required 16384-byte external-call reserve: $unsafeMessage"
    }

    Set-FixtureTaskStack 32768
    & $verifier -FirmwareDirectory $testRoot -BuildDirectory $BuildDirectory | Out-Null

    Write-Output 'Transport stack-budget regression passed: 10240 rejected and 32768 accepted'
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
