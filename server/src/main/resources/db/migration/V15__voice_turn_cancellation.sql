alter table voice_turns drop constraint voice_turns_status_check;
alter table voice_turns drop constraint voice_turn_failure_check;

alter table voice_turns add constraint voice_turns_status_check check (
  status in ('IN_PROGRESS', 'RESPONSE_READY', 'COMPLETED', 'CANCELLED', 'FAILED')
);
alter table voice_turns add constraint voice_turn_failure_check check (
  (status = 'FAILED' and failure_code is not null) or
  (status <> 'FAILED' and failure_code is null)
);

alter table voice_turn_events drop constraint voice_turn_events_stage_check;
alter table voice_turn_events add constraint voice_turn_events_stage_check check (stage in (
  'WAKE_DETECTED', 'TOUCH_STARTED', 'LISTENING', 'SPEECH_CAPTURED', 'UPLOAD_STARTED',
  'REQUEST_RECEIVED', 'ASR_COMPLETED', 'LLM_COMPLETED', 'TTS_COMPLETED',
  'PLAYBACK_STARTED', 'PLAYBACK_COMPLETED', 'LISTENING_RESUMED', 'CANCELLED', 'FAILED'
));
