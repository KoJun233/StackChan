create table device_quiet_today (
  device_id uuid primary key references devices(id) on delete cascade,
  paused_until timestamptz not null,
  updated_at timestamptz not null
);
