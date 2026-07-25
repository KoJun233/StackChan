create table voice_turns (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  status varchar(24) not null check (status in ('IN_PROGRESS', 'RESPONSE_READY', 'COMPLETED', 'FAILED')),
  failure_code varchar(40) check (failure_code is null or failure_code in (
    'NO_SPEECH', 'OFFLINE', 'OUT_OF_MEMORY', 'UPLOAD_FAILED', 'INVALID_RESPONSE',
    'PLAYBACK_FAILED', 'MICROPHONE_RECOVERY_FAILED', 'ASR_UNAVAILABLE',
    'LLM_UNAVAILABLE', 'TTS_UNAVAILABLE', 'INTERNAL_ERROR'
  )),
  started_at timestamptz not null,
  updated_at timestamptz not null,
  constraint voice_turn_failure_check check (
    (status = 'FAILED' and failure_code is not null) or
    (status <> 'FAILED' and failure_code is null)
  )
);

create table voice_turn_events (
  id uuid primary key,
  turn_id uuid not null references voice_turns(id) on delete cascade,
  stage varchar(40) not null check (stage in (
    'WAKE_DETECTED', 'LISTENING', 'SPEECH_CAPTURED', 'UPLOAD_STARTED',
    'REQUEST_RECEIVED', 'ASR_COMPLETED', 'LLM_COMPLETED', 'TTS_COMPLETED',
    'PLAYBACK_STARTED', 'PLAYBACK_COMPLETED', 'LISTENING_RESUMED', 'FAILED'
  )),
  source varchar(16) not null check (source in ('DEVICE', 'SERVER')),
  occurred_at timestamptz not null,
  elapsed_ms integer check (elapsed_ms is null or (elapsed_ms >= 0 and elapsed_ms <= 300000)),
  failure_code varchar(40) check (failure_code is null or failure_code in (
    'NO_SPEECH', 'OFFLINE', 'OUT_OF_MEMORY', 'UPLOAD_FAILED', 'INVALID_RESPONSE',
    'PLAYBACK_FAILED', 'MICROPHONE_RECOVERY_FAILED', 'ASR_UNAVAILABLE',
    'LLM_UNAVAILABLE', 'TTS_UNAVAILABLE', 'INTERNAL_ERROR'
  )),
  constraint voice_turn_event_failure_check check (
    (stage = 'FAILED' and failure_code is not null) or
    (stage <> 'FAILED' and failure_code is null)
  ),
  constraint voice_turn_event_stage_uq unique (turn_id, source, stage)
);

create index voice_turns_device_started_idx on voice_turns(device_id, started_at desc);
create index voice_turns_retention_idx on voice_turns(started_at);
create index voice_turn_events_turn_occurred_idx on voice_turn_events(turn_id, occurred_at, id);
