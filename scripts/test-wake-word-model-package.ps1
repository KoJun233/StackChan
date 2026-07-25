$ErrorActionPreference = 'Stop'

$verifier = Join-Path $PSScriptRoot 'verify-wake-word-model-package.ps1'
$systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = [IO.Path]::GetFullPath((Join-Path $systemTemp "stackchan-wake-model-$([Guid]::NewGuid())"))

if (-not $testRoot.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create wake-model fixtures outside the system temp directory: $testRoot"
}

function New-ModelFixture(
    [string]$Root,
    [string]$Name,
    [int]$DataBytes = 128,
    [string]$ModelInfo = 'wakenet9l_tts3h12_Test Phrase_3_0.50_0.50'
) {
    $directory = New-Item -ItemType Directory -Path (Join-Path $Root $Name) -Force
    [IO.File]::WriteAllText(
        (Join-Path $directory.FullName '_MODEL_INFO_'),
        $ModelInfo,
        [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllBytes((Join-Path $directory.FullName 'wn9_index'), [byte[]](1..16))
    $stream = [IO.File]::OpenWrite((Join-Path $directory.FullName 'wn9_data'))
    try {
        $stream.SetLength($DataBytes)
    }
    finally {
        $stream.Dispose()
    }
    return $directory.FullName
}

function Assert-VerifierFails([scriptblock]$Action, [string]$ExpectedPattern) {
    $failed = $false
    $message = ''
    try {
        & $Action
    }
    catch {
        $failed = $true
        $message = $_.Exception.Message
    }
    if (-not $failed) {
        throw "Wake-model verifier unexpectedly passed; expected: $ExpectedPattern"
    }
    if ($message -notmatch $ExpectedPattern) {
        throw "Wake-model verifier failed with an unexpected message: $message"
    }
}

try {
    $defaultRoot = New-Item -ItemType Directory -Path (Join-Path $testRoot 'default') -Force
    $customRoot = New-Item -ItemType Directory -Path (Join-Path $testRoot 'custom') -Force
    $defaultModel = New-ModelFixture $defaultRoot.FullName 'wn9l_histackchan_tts3'
    New-ModelFixture $customRoot.FullName 'wn9l_custom_companion_tts3' | Out-Null

    & $verifier -CustomModelDirectory $customRoot.FullName -DefaultModelDirectory $defaultModel `
        -PartitionBytes 4096 -MinimumHeadroomBytes 256 | Out-Null

    $missingRoot = New-Item -ItemType Directory -Path (Join-Path $testRoot 'missing-info') -Force
    New-Item -ItemType Directory -Path (Join-Path $missingRoot.FullName 'wn9l_missing_tts3') -Force | Out-Null
    Assert-VerifierFails {
        & $verifier -CustomModelDirectory $missingRoot.FullName -DefaultModelDirectory $defaultModel
    } 'contains no WakeNet model directory'

    $duplicateRoot = New-Item -ItemType Directory -Path (Join-Path $testRoot 'duplicate') -Force
    New-ModelFixture $duplicateRoot.FullName 'wn9l_histackchan_tts3' | Out-Null
    Assert-VerifierFails {
        & $verifier -CustomModelDirectory $duplicateRoot.FullName -DefaultModelDirectory $defaultModel
    } 'must not replace an existing packaged model'

    $invalidRoot = New-Item -ItemType Directory -Path (Join-Path $testRoot 'invalid') -Force
    New-ModelFixture $invalidRoot.FullName 'not_a_wakenet_model' | Out-Null
    Assert-VerifierFails {
        & $verifier -CustomModelDirectory $invalidRoot.FullName -DefaultModelDirectory $defaultModel
    } 'lowercase WakeNet identifier'

    $damagedRoot = New-Item -ItemType Directory -Path (Join-Path $testRoot 'damaged') -Force
    New-ModelFixture $damagedRoot.FullName 'wn9l_damaged_tts3' 128 'not_model_metadata' | Out-Null
    Assert-VerifierFails {
        & $verifier -CustomModelDirectory $damagedRoot.FullName -DefaultModelDirectory $defaultModel
    } 'valid WakeNet metadata record'

    $oversizedRoot = New-Item -ItemType Directory -Path (Join-Path $testRoot 'oversized') -Force
    New-ModelFixture $oversizedRoot.FullName 'wn9l_oversized_tts3' 4096 | Out-Null
    Assert-VerifierFails {
        & $verifier -CustomModelDirectory $oversizedRoot.FullName -DefaultModelDirectory $defaultModel `
            -PartitionBytes 4096 -MinimumHeadroomBytes 256
    } 'exceed the safe partition budget'

    Write-Output 'Wake-word model package regression passed: valid package accepted and unsafe fixtures rejected'
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
