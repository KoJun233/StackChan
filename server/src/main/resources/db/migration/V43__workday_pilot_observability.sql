alter table devices
  add column last_device_sequence bigint;

alter table workday_daily_metrics
  add column false_trigger_count integer not null default 0 check (false_trigger_count >= 0),
  add column motion_rejected_count integer not null default 0 check (motion_rejected_count >= 0),
  add column motion_failed_count integer not null default 0 check (motion_failed_count >= 0),
  add column device_restart_count integer not null default 0 check (device_restart_count >= 0),
  add column calendar_failure_count integer not null default 0 check (calendar_failure_count >= 0),
  add column calendar_last_failure_code varchar(32),
  add column weather_failure_count integer not null default 0 check (weather_failure_count >= 0),
  add column weather_last_failure_code varchar(32);

alter table workday_daily_metrics
  add constraint workday_calendar_failure_code_check check (
    calendar_last_failure_code is null or calendar_last_failure_code in (
      'AUTHENTICATION_FAILED', 'DISCOVERY_FAILED', 'SYNC_FAILED',
      'RESPONSE_TOO_LARGE', 'INVALID_RESPONSE', 'STALE_CACHE', 'UNAVAILABLE'
    )
  ),
  add constraint workday_weather_failure_code_check check (
    weather_last_failure_code is null or weather_last_failure_code in (
      'REQUEST_FAILED', 'RESPONSE_TOO_LARGE', 'INVALID_RESPONSE',
      'STALE_CACHE', 'UNAVAILABLE'
    )
  ),
  add constraint workday_calendar_failure_attribution_check check (
    calendar_failure_count = 0 or calendar_last_failure_code is not null
  ),
  add constraint workday_weather_failure_attribution_check check (
    weather_failure_count = 0 or weather_last_failure_code is not null
  );

create table workday_pilot_observations (
  device_id uuid primary key references devices(id) on delete cascade,
  started_on date not null,
  ends_on date not null,
  zone_id varchar(64) not null,
  work_days_mask smallint not null check (work_days_mask between 1 and 127),
  started_at timestamptz not null,
  updated_at timestamptz not null,
  constraint workday_pilot_window_check check (ends_on = started_on + 13)
);
