alter table voice_turn_events
  add column duration_ms integer,
  add column diagnostic_code varchar(80),
  add constraint voice_turn_events_duration_check
    check (duration_ms is null or duration_ms between 0 and 300000),
  add constraint voice_turn_events_diagnostic_check
    check (diagnostic_code is null or diagnostic_code ~ '^[a-z0-9_]{1,80}$');

alter table speech_provider_settings alter column wake_sensitivity set default 'NORMAL';

alter table voice_turn_events drop constraint voice_turn_events_stage_check;
alter table voice_turn_events add constraint voice_turn_events_stage_check check (stage in (
  'WAKE_DETECTED', 'TOUCH_STARTED', 'LISTENING', 'FOLLOW_UP_LISTENING', 'SPEECH_CAPTURED',
  'UPLOAD_STARTED', 'REQUEST_RECEIVED', 'ASR_COMPLETED', 'LLM_COMPLETED', 'TTS_COMPLETED',
  'PLAYBACK_STARTED', 'PLAYBACK_COMPLETED', 'FOLLOW_UP_TIMEOUT', 'CONVERSATION_ENDED',
  'LISTENING_RESUMED', 'MANUAL_INPUT_READY', 'CANCELLED', 'FAILED'
));
