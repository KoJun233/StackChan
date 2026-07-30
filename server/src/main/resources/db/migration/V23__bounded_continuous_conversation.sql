alter table device_interaction_settings
  add column continuous_conversation_enabled boolean not null default false,
  add column follow_up_window_seconds integer not null default 8
    check (follow_up_window_seconds between 3 and 8);

alter table voice_turn_events drop constraint voice_turn_events_stage_check;
alter table voice_turn_events add constraint voice_turn_events_stage_check check (stage in (
  'WAKE_DETECTED', 'TOUCH_STARTED', 'LISTENING', 'FOLLOW_UP_LISTENING',
  'SPEECH_CAPTURED', 'UPLOAD_STARTED', 'REQUEST_RECEIVED', 'ASR_COMPLETED',
  'LLM_COMPLETED', 'TTS_COMPLETED', 'PLAYBACK_STARTED', 'PLAYBACK_COMPLETED',
  'FOLLOW_UP_TIMEOUT', 'CONVERSATION_ENDED', 'LISTENING_RESUMED', 'CANCELLED', 'FAILED'
));
