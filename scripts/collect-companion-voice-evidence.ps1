param(
    [DateTimeOffset]$Since = [DateTimeOffset]::UtcNow.AddMinutes(-30),
    [Guid]$DeviceId = '0c4d09ea-ff9b-4701-9702-428c01cac264'
)

# Read structured diagnostics only: no transcript, audio, message or credential queries.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'companion-voice-evidence-statistics.ps1')
$utc = $Since.ToUniversalTime().ToString('o')
$sql = @"
select coalesce(json_agg(row_to_json(t) order by t.started_at), '[]'::json) from (
  select v.id as turn_id, v.started_at, v.status, v.failure_code,
    (select stage from voice_turn_events e where e.turn_id=v.id and e.source='DEVICE'
      and e.stage in ('WAKE_DETECTED','TOUCH_STARTED') order by e.occurred_at limit 1) as input_trigger,
    (select elapsed_ms from voice_turn_events e where e.turn_id=v.id and e.source='DEVICE'
      and e.stage='SPEECH_CAPTURED') as capture_end_ms,
    (select elapsed_ms from voice_turn_events e where e.turn_id=v.id and e.source='DEVICE'
      and e.stage='PLAYBACK_STARTED') as first_audio_ms,
    (select duration_ms from voice_turn_events e where e.turn_id=v.id and e.source='SERVER'
      and e.stage='ASR_COMPLETED') as asr_ms,
    (select duration_ms from voice_turn_events e where e.turn_id=v.id and e.source='SERVER'
      and e.stage='LLM_COMPLETED') as model_ms,
    (select diagnostic_code from voice_turn_events e where e.turn_id=v.id and e.source='SERVER'
      and e.stage='LLM_COMPLETED') as model_path,
    (select duration_ms from voice_turn_events e where e.turn_id=v.id and e.source='SERVER'
      and e.stage='TTS_COMPLETED') as tts_ms,
    (select stage from voice_turn_events e where e.turn_id=v.id and e.source='DEVICE'
      and e.stage in ('LISTENING_RESUMED','MANUAL_INPUT_READY') order by e.occurred_at desc limit 1) as input_ready,
    (select diagnostic_code from voice_turn_events e where e.turn_id=v.id and e.stage='FAILED'
      and diagnostic_code is not null order by e.occurred_at desc limit 1) as diagnostic_code
  from voice_turns v where v.device_id='$DeviceId' and v.started_at >= '$utc'::timestamptz
  order by v.started_at desc limit 200
) t;
"@
$raw = & docker.exe exec stackchan-foundation-postgres-1 psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan -c $sql 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Structured diagnostic query failed; private output suppressed.' }
$rows = @((($raw -join "`n") | ConvertFrom-Json) | Where-Object { $null -ne $_ })
$mixed = Get-CompanionVoiceLatencySummary -Turns $rows
[ordered]@{
    sinceUtc = $utc
    deviceId = $DeviceId
    recordedTurns = $rows.Count
    timedCompletedTurns = $mixed.timedCompletedTurns
    captureEndToFirstAudioMedianMs = $mixed.captureEndToFirstAudioMedianMs
    captureEndToFirstAudioP95Ms = $mixed.captureEndToFirstAudioP95Ms
    timingScope = 'Device capture end to first playback; not the final spoken syllable. Correctness and false wakes require user observation.'
    sampleScope = 'Mixed usage, not a controlled short-phrase benchmark. ModelTimed does not prove that no tools were used.'
    scenarioSummaries = @(
        $mixed,
        (Get-CompanionVoiceLatencySummary -Turns $rows -Scenario ModelTimed),
        (Get-CompanionVoiceLatencySummary -Turns $rows -Scenario DeterministicAction)
    )
    turns = $rows
} | ConvertTo-Json -Depth 6
