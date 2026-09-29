create table body_motion_commands (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  motion varchar(16) not null check (motion in ('WAKE', 'LOOK_USER', 'NOD_SMALL', 'THINK', 'DROWSY')),
  automatic boolean not null default false,
  event_key varchar(120),
  status varchar(24) not null check (status in ('SENT', 'ACCEPTED', 'REJECTED', 'COMPLETED', 'STOPPED', 'FAILED', 'DELIVERY_FAILED', 'UNCONFIRMED')),
  failure_code varchar(32),
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create unique index body_motion_commands_device_event_idx
  on body_motion_commands(device_id, event_key) where event_key is not null;

create table body_motion_auto_settings (
  device_id uuid primary key references devices(id) on delete cascade,
  enabled boolean not null default false
);

create index body_motion_commands_device_created_idx
  on body_motion_commands(device_id, created_at desc);
