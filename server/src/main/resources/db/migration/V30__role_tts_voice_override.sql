alter table companion_roles add column tts_voice_override varchar(160);

alter table companion_roles add constraint companion_roles_tts_voice_override_check
  check (tts_voice_override is null or length(trim(tts_voice_override)) between 1 and 160);
