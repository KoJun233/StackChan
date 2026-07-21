param(
    [string]$FirmwareDirectory = (Join-Path $PSScriptRoot '..\firmware'),
    [string]$BuildDirectory = 'build-stack-analysis'
)

$ErrorActionPreference = 'Stop'

$verifier = Join-Path $PSScriptRoot 'verify-firmware-provisioning-stack-budget.ps1'
$sourcePath = Join-Path $FirmwareDirectory 'main\device_provisioning.c'
$systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = [IO.Path]::GetFullPath((Join-Path $systemTemp "stackchan-stack-budget-$([Guid]::NewGuid())"))

if (-not $testRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create stack-budget fixture outside the system temp directory: $testRoot"
}

function Set-FixtureTaskStack([int]$Bytes) {
    $fixtureSource = Join-Path $testRoot 'main\device_provisioning.c'
    $source = Get-Content -LiteralPath $fixtureSource -Raw
    $updated = [regex]::Replace(
        $source,
        '(?m)^#define\s+PROVISIONING_TASK_STACK_SIZE\s+\d+\r?$',
        "#define PROVISIONING_TASK_STACK_SIZE $Bytes"
    )
    if ($updated -eq $source) {
        throw 'Fixture task stack size was not replaced'
    }
    [IO.File]::WriteAllText($fixtureSource, $updated, [Text.UTF8Encoding]::new($false))
}

try {
    New-Item -ItemType Directory -Path (Join-Path $testRoot 'main') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $testRoot "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir") -Force | Out-Null
    Copy-Item -LiteralPath $sourcePath -Destination (Join-Path $testRoot 'main\device_provisioning.c')
    $fixtureUsagePath = Join-Path $testRoot "$BuildDirectory\esp-idf\main\CMakeFiles\__idf_main.dir\device_provisioning.c.su"
    [IO.File]::WriteAllLines($fixtureUsagePath, @(
        'device_provisioning.c:100:1:provisioning_task 512 static'
        'device_provisioning.c:200:1:provision 512 static'
        'device_provisioning.c:300:1:claim_device 512 static'
        'device_provisioning.c:400:1:device_provisioning_parse_claim_response 512 static'
    ), [Text.UTF8Encoding]::new($false))

    Set-FixtureTaskStack 8192
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
        throw 'The unsafe 8192-byte provisioning task budget unexpectedly passed'
    }
    if ($unsafeMessage -notmatch 'requiredHeadroom=8192 bytes') {
        throw "The unsafe budget failed without the required 8192-byte external-call reserve: $unsafeMessage"
    }

    Set-FixtureTaskStack 16384
    & $verifier -FirmwareDirectory $testRoot -BuildDirectory $BuildDirectory | Out-Null

    Write-Output 'Provisioning stack-budget regression passed: 8192 rejected and 16384 accepted'
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
