create table device_workday_settings (
  device_id uuid primary key references devices(id) on delete cascade,
  enabled boolean not null default false,
  work_days_mask integer not null default 31 check (work_days_mask between 1 and 127),
  work_start time not null default time '09:00',
  work_end time not null default time '18:00',
  focus_minutes integer not null default 50 check (focus_minutes between 15 and 180),
  rest_minutes integer not null default 10 check (rest_minutes between 5 and 60),
  absence_suspend_minutes integer not null default 10 check (absence_suspend_minutes between 1 and 60),
  rearrival_minutes integer not null default 45 check (rearrival_minutes between 5 and 240),
  location_name varchar(120) not null default '',
  latitude double precision,
  longitude double precision,
  zone_id varchar(80) not null default 'Asia/Shanghai',
  updated_at timestamptz not null,
  constraint device_workday_rearrival_check
    check (rearrival_minutes >= absence_suspend_minutes),
  constraint device_workday_location_pair_check
    check ((latitude is null and longitude is null) or (latitude is not null and longitude is not null)),
  constraint device_workday_latitude_check
    check (latitude is null or latitude between -90 and 90),
  constraint device_workday_longitude_check
    check (longitude is null or longitude between -180 and 180)
);
