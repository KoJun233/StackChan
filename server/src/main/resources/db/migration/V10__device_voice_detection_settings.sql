alter table speech_provider_settings
  add column wake_sensitivity varchar(32) not null default 'SENSITIVE',
  add column speech_start_threshold integer not null default 350,
  add column speech_silence_threshold integer not null default 200;

alter table speech_provider_settings
  add constraint speech_wake_sensitivity_check
    check (wake_sensitivity in ('NORMAL', 'SENSITIVE')),
  add constraint speech_start_threshold_check
    check (speech_start_threshold between 100 and 5000),
  add constraint speech_silence_threshold_check
    check (speech_silence_threshold between 50 and 4000),
  add constraint speech_detection_threshold_order_check
    check (speech_silence_threshold < speech_start_threshold);
