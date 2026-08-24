create table icloud_calendar_connections (
  device_id uuid primary key references devices(id) on delete cascade,
  account_email varchar(320) not null,
  password_ciphertext text not null,
  password_iv varchar(64) not null,
  principal_url varchar(2048),
  calendar_home_url varchar(2048),
  status varchar(24) not null,
  last_failure_code varchar(40),
  last_tested_at timestamptz,
  last_synced_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint icloud_calendar_connection_status_check check (status in (
    'CONFIGURED', 'CONNECTED', 'AUTH_FAILED', 'ERROR'
  )),
  constraint icloud_calendar_failure_code_check check (
    last_failure_code is null or last_failure_code in (
      'AUTHENTICATION_FAILED', 'DISCOVERY_FAILED', 'SYNC_FAILED',
      'RESPONSE_TOO_LARGE', 'INVALID_RESPONSE'
    )
  )
);

create table icloud_calendars (
  id uuid primary key,
  device_id uuid not null references icloud_calendar_connections(device_id) on delete cascade,
  calendar_key char(64) not null,
  calendar_href varchar(2048) not null,
  display_name varchar(255) not null,
  allowed boolean not null default false,
  discovered_at timestamptz not null,
  updated_at timestamptz not null,
  constraint icloud_calendar_device_key_unique unique (device_id, calendar_key)
);

create index icloud_calendars_device_allowed_idx
  on icloud_calendars (device_id, allowed);

create table workday_calendar_events (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  calendar_id uuid not null references icloud_calendars(id) on delete cascade,
  event_key char(64) not null,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  all_day boolean not null default false,
  busy boolean not null default true,
  private_event boolean not null default false,
  title varchar(512) not null,
  location varchar(512),
  fetched_at timestamptz not null,
  expires_at timestamptz not null,
  constraint workday_calendar_event_calendar_key_unique unique (calendar_id, event_key),
  constraint workday_calendar_event_time_check check (ends_at >= starts_at)
);

create index workday_calendar_events_device_time_idx
  on workday_calendar_events (device_id, starts_at, expires_at);
