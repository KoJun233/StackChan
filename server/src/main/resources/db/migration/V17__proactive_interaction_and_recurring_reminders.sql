alter table reminders
  add column recurrence_type varchar(16) not null default 'NONE',
  add column recurrence_interval integer not null default 1,
  add column recurrence_anchor_local timestamp,
  add column source varchar(16) not null default 'USER',
  add column last_outcome varchar(24),
  add column last_completed_at timestamptz;

alter table reminders
  add constraint reminders_recurrence_type_check
    check (recurrence_type in ('NONE', 'DAILY', 'WEEKLY')),
  add constraint reminders_recurrence_interval_check
    check (recurrence_interval between 1 and 365),
  add constraint reminders_source_check
    check (source in ('USER', 'PROACTIVE')),
  add constraint reminders_last_outcome_check
    check (last_outcome is null or last_outcome in ('DELIVERED', 'FAILED', 'CANCELLED', 'SKIPPED'));

alter table reminders drop constraint if exists reminders_status_check;
alter table reminders
  add constraint reminders_status_check
    check (status in ('PENDING', 'DISPATCHED', 'DELIVERED', 'FAILED', 'CANCELLED', 'SKIPPED'));

create table device_interaction_settings (
  device_id uuid primary key references devices(id) on delete cascade,
  volume_percent integer not null default 50 check (volume_percent between 0 and 100),
  night_mode boolean not null default false,
  dnd_enabled boolean not null default false,
  dnd_start time not null default time '22:00',
  dnd_end time not null default time '07:00',
  zone_id varchar(80) not null default 'Asia/Shanghai',
  missed_reminder_policy varchar(16) not null default 'PLAY_NOW'
    check (missed_reminder_policy in ('PLAY_NOW', 'SNOOZE', 'SKIP')),
  missed_snooze_minutes integer not null default 10 check (missed_snooze_minutes between 1 and 1440),
  proactive_enabled boolean not null default false,
  proactive_start time not null default time '09:00',
  proactive_end time not null default time '21:00',
  proactive_min_interval_minutes integer not null default 240
    check (proactive_min_interval_minutes between 30 and 1440),
  proactive_daily_limit integer not null default 2 check (proactive_daily_limit between 1 and 10),
  proactive_content varchar(500) not null default '你好呀，记得休息一下，也可以和我聊聊天。',
  proactive_last_at timestamptz,
  proactive_counter_date date,
  proactive_counter integer not null default 0 check (proactive_counter between 0 and 10),
  updated_at timestamptz not null
);
