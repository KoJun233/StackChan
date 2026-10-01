function Get-CompanionVoiceLatencySummary {
    param(
        [AllowEmptyCollection()][object[]]$Turns = @(),
        [ValidateSet('Mixed', 'ModelTimed', 'DeterministicAction')][string]$Scenario = 'Mixed'
    )

    $selected = @($Turns | Where-Object {
        $null -ne $_ -and (
            $Scenario -eq 'Mixed' -or
            ($Scenario -eq 'ModelTimed' -and $null -ne $_.model_ms -and
                $_.model_path -ne 'deterministic_action') -or
            ($Scenario -eq 'DeterministicAction' -and $_.model_path -eq 'deterministic_action')
        )
    })
    $latencies = @($selected | Where-Object {
        $_.status -eq 'COMPLETED' -and $null -ne $_.capture_end_ms -and $null -ne $_.first_audio_ms -and
        $_.capture_end_ms -ge 0 -and $_.capture_end_ms -le 300000 -and
        $_.first_audio_ms -ge $_.capture_end_ms -and $_.first_audio_ms -le 300000
    } | ForEach-Object { [int]$_.first_audio_ms - [int]$_.capture_end_ms } | Sort-Object)
    $median = $null
    $p95 = $null
    if ($latencies.Count -gt 0) {
        $middle = [int][Math]::Floor($latencies.Count / 2)
        $median = if ($latencies.Count % 2) { $latencies[$middle] }
            else { ($latencies[$middle - 1] + $latencies[$middle]) / 2 }
        $p95 = $latencies[[int][Math]::Ceiling($latencies.Count * 0.95) - 1]
    }
    [pscustomobject][ordered]@{
        scenario = $Scenario
        recordedTurns = $selected.Count
        completedTurns = @($selected | Where-Object { $_.status -eq 'COMPLETED' }).Count
        failedTurns = @($selected | Where-Object { $_.status -eq 'FAILED' }).Count
        cancelledTurns = @($selected | Where-Object { $_.status -eq 'CANCELLED' }).Count
        timedCompletedTurns = $latencies.Count
        captureEndToFirstAudioMedianMs = $median
        captureEndToFirstAudioP95Ms = $p95
    }
}
