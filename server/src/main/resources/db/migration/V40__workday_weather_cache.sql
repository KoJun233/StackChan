create table workday_weather_states (
  device_id uuid primary key references devices(id) on delete cascade,
  location_name varchar(120) not null,
  latitude double precision not null,
  longitude double precision not null,
  zone_id varchar(80) not null,
  status varchar(16) not null,
  last_failure_code varchar(32),
  last_attempted_at timestamptz not null,
  last_synced_at timestamptz,
  cache_expires_at timestamptz,
  current_observed_at timestamptz,
  current_temperature double precision,
  current_apparent_temperature double precision,
  current_precipitation double precision,
  current_weather_code integer,
  updated_at timestamptz not null,
  constraint workday_weather_status_check check (status in ('READY', 'ERROR')),
  constraint workday_weather_failure_code_check check (
    last_failure_code is null or last_failure_code in (
      'REQUEST_FAILED', 'RESPONSE_TOO_LARGE', 'INVALID_RESPONSE'
    )
  ),
  constraint workday_weather_coordinates_check check (
    latitude between -90 and 90 and longitude between -180 and 180
  ),
  constraint workday_weather_current_group_check check (
    (last_synced_at is null and cache_expires_at is null and current_observed_at is null
      and current_temperature is null and current_apparent_temperature is null
      and current_precipitation is null and current_weather_code is null)
    or
    (last_synced_at is not null and cache_expires_at is not null and current_observed_at is not null
      and current_temperature is not null and current_apparent_temperature is not null
      and current_precipitation is not null and current_weather_code is not null)
  )
);

create table workday_weather_daily_forecasts (
  id uuid primary key,
  device_id uuid not null references workday_weather_states(device_id) on delete cascade,
  forecast_date date not null,
  weather_code integer not null,
  temperature_max double precision not null,
  temperature_min double precision not null,
  apparent_temperature_max double precision not null,
  apparent_temperature_min double precision not null,
  precipitation_probability_max integer not null,
  precipitation_sum double precision not null,
  constraint workday_weather_device_date_unique unique (device_id, forecast_date),
  constraint workday_weather_probability_check check (
    precipitation_probability_max between 0 and 100
  ),
  constraint workday_weather_precipitation_check check (precipitation_sum >= 0)
);

create index workday_weather_daily_device_date_idx
  on workday_weather_daily_forecasts (device_id, forecast_date);
