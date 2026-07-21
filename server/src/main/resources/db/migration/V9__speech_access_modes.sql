alter table speech_provider_settings
  add column asr_mode varchar(32) not null default 'REALTIME',
  add column tts_mode varchar(32) not null default 'NON_REALTIME';

alter table speech_provider_settings
  add constraint speech_asr_mode_check
    check (asr_mode in ('REALTIME', 'NON_REALTIME')),
  add constraint speech_tts_mode_check
    check (tts_mode in ('REALTIME', 'NON_REALTIME'));
