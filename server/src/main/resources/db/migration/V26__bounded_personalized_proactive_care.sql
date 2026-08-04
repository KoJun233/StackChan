alter table device_interaction_settings
  add column proactive_personalization_enabled boolean not null default false;

alter table reminders
  add column proactive_topic_key varchar(120),
  add column proactive_generation_status varchar(16);

alter table reminders
  add constraint reminders_proactive_generation_status_check check (
    proactive_generation_status is null
    or proactive_generation_status in ('FIXED', 'GENERATED', 'FALLBACK')
  ),
  add constraint reminders_proactive_metadata_check check (
    (source = 'PROACTIVE')
    or (proactive_topic_key is null and proactive_generation_status is null)
  );

create table proactive_topic_cooldowns (
  device_id uuid not null references devices(id) on delete cascade,
  topic_key varchar(120) not null,
  last_mentioned_at timestamptz not null,
  cooldown_until timestamptz not null,
  user_muted boolean not null default false,
  updated_at timestamptz not null,
  primary key (device_id, topic_key),
  constraint proactive_topic_cooldowns_topic_check check (length(trim(topic_key)) between 1 and 120)
);

create index proactive_topic_cooldowns_recent_idx
  on proactive_topic_cooldowns(device_id, last_mentioned_at desc, topic_key);
