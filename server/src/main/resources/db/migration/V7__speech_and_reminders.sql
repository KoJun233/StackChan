create table speech_provider_settings (
  id smallint primary key,
  base_url varchar(2048) not null,
  asr_model varchar(160) not null,
  tts_model varchar(160) not null,
  tts_voice varchar(160) not null,
  api_key_ciphertext text not null,
  api_key_iv varchar(64) not null,
  updated_at timestamptz not null
);

create table device_voice_conversations (
  device_id uuid primary key references devices(id) on delete cascade,
  conversation_id uuid not null unique references conversations(id) on delete cascade
);

create table reminders (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  content varchar(1000) not null,
  scheduled_at timestamptz not null,
  zone_id varchar(80) not null,
  status varchar(24) not null check (status in ('PENDING', 'DISPATCHED', 'DELIVERED', 'FAILED', 'CANCELLED')),
  command_id varchar(96),
  attempt_count integer not null default 0,
  last_attempt_at timestamptz,
  failure_code varchar(80),
  audio_payload bytea,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index reminders_due_idx on reminders(status, scheduled_at, id);
create unique index reminders_command_uq on reminders(command_id) where command_id is not null;
