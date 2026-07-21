alter table speech_provider_settings
  add column provider_type varchar(32) not null default 'OPENAI_COMPATIBLE',
  add column workspace_id varchar(160);

alter table speech_provider_settings
  add constraint speech_provider_type_check
    check (provider_type in ('OPENAI_COMPATIBLE', 'DASHSCOPE'));
