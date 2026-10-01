$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'companion-voice-evidence-statistics.ps1')

function Assert-Equal($Actual, $Expected, [string]$Label) {
    if ($Actual -ne $Expected) { throw "$Label expected=$Expected actual=$Actual" }
}

function New-TestTurn([int]$Latency, $ModelMs = $null, $Path = $null, [string]$Status = 'COMPLETED') {
    [pscustomobject]@{ status = $Status; capture_end_ms = 100; first_audio_ms = 100 + $Latency
        model_ms = $ModelMs; model_path = $Path }
}

$empty = Get-CompanionVoiceLatencySummary
Assert-Equal $empty.recordedTurns 0 'Empty sample count'
Assert-Equal $empty.captureEndToFirstAudioMedianMs $null 'Empty median remains unknown'
Assert-Equal $empty.captureEndToFirstAudioP95Ms $null 'Empty P95 remains unknown'

$turns = @(
    (New-TestTurn 300 20),
    (New-TestTurn 200 $null 'deterministic_action'),
    (New-TestTurn 100 0),
    (New-TestTurn 50 20 $null 'CANCELLED'),
    (New-TestTurn 50 $null $null 'FAILED')
)
$mixed = Get-CompanionVoiceLatencySummary -Turns $turns
Assert-Equal $mixed.recordedTurns 5 'All attempts retained'
Assert-Equal $mixed.completedTurns 3 'Completed count'
Assert-Equal $mixed.cancelledTurns 1 'Cancelled count'
Assert-Equal $mixed.failedTurns 1 'Failed count'
Assert-Equal $mixed.timedCompletedTurns 3 'Only completed timings included'
Assert-Equal $mixed.captureEndToFirstAudioMedianMs 200 'Odd median'
Assert-Equal $mixed.captureEndToFirstAudioP95Ms 300 'Nearest-rank P95'
$model = Get-CompanionVoiceLatencySummary -Turns $turns -Scenario ModelTimed
Assert-Equal $model.recordedTurns 3 'Model attempts not hidden'
Assert-Equal $model.timedCompletedTurns 2 'Model and deterministic action separated'
Assert-Equal $model.captureEndToFirstAudioMedianMs 200 'Even median preserves both middle values'
$action = Get-CompanionVoiceLatencySummary -Turns $turns -Scenario DeterministicAction
Assert-Equal $action.timedCompletedTurns 1 'Deterministic action sample'
Assert-Equal $action.captureEndToFirstAudioMedianMs 200 'Action median'

$invalid = @(
    [pscustomobject]@{ status='COMPLETED'; capture_end_ms=$null; first_audio_ms=100 },
    [pscustomobject]@{ status='COMPLETED'; capture_end_ms=100; first_audio_ms=$null },
    [pscustomobject]@{ status='COMPLETED'; capture_end_ms=100; first_audio_ms=99 },
    [pscustomobject]@{ status='COMPLETED'; capture_end_ms=-1; first_audio_ms=100 },
    [pscustomobject]@{ status='COMPLETED'; capture_end_ms=100; first_audio_ms=300001 }
)
$unknown = Get-CompanionVoiceLatencySummary -Turns $invalid
Assert-Equal $unknown.completedTurns 5 'Missing timings do not erase completion records'
Assert-Equal $unknown.timedCompletedTurns 0 'Missing, reversed and out-of-bounds timings excluded'
Assert-Equal $unknown.captureEndToFirstAudioMedianMs $null 'Invalid timing median is unknown'
Assert-Equal (Get-CompanionVoiceLatencySummary -Turns $invalid -Scenario ModelTimed).recordedTurns 0 'Unknown model path is not classified'

$twenty = @(1..20 | ForEach-Object { New-TestTurn $_ })
$percentile = Get-CompanionVoiceLatencySummary -Turns $twenty
Assert-Equal $percentile.captureEndToFirstAudioMedianMs 10.5 'Twenty-sample median'
Assert-Equal $percentile.captureEndToFirstAudioP95Ms 19 'Twenty-sample nearest-rank P95'
Write-Output 'PASS voice evidence: scenario separation, failures/cancellations, missing/bounded timings and percentiles'
