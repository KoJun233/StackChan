create table device_workday_runtime (
  device_id uuid primary key references devices(id) on delete cascade,
  state varchar(32) not null,
  work_date date,
  present boolean not null default false,
  focus_seconds bigint not null default 0 check (focus_seconds >= 0),
  started_at timestamptz,
  state_changed_at timestamptz not null,
  focus_updated_at timestamptz,
  absence_started_at timestamptz,
  rest_until timestamptz,
  snoozed_until timestamptz,
  updated_at timestamptz not null,
  constraint device_workday_runtime_state_check check (state in (
    'OFF', 'STARTING', 'ACTIVE_PRESENT', 'ACTIVE_ABSENT',
    'REST_PROMPTED', 'RESTING', 'SKIPPED_FOR_DAY'
  )),
  constraint device_workday_runtime_date_check check (
    (state = 'OFF' and work_date is null) or (state <> 'OFF' and work_date is not null)
  )
);

create table workday_brief_attempts (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  work_date date not null,
  status varchar(16) not null,
  claimed_at timestamptz not null,
  completed_at timestamptz,
  updated_at timestamptz not null,
  constraint workday_brief_attempt_unique unique (device_id, work_date),
  constraint workday_brief_status_check check (status in (
    'PENDING', 'SUCCESS', 'PARTIAL', 'FAILED', 'CANCELLED'
  )),
  constraint workday_brief_completion_check check (
    (status = 'PENDING' and completed_at is null) or
    (status <> 'PENDING' and completed_at is not null)
  )
);

create index workday_brief_attempts_date_idx on workday_brief_attempts(work_date);

create table workday_daily_metrics (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  work_date date not null,
  session_start_count integer not null default 0 check (session_start_count >= 0),
  session_end_count integer not null default 0 check (session_end_count >= 0),
  focus_seconds bigint not null default 0 check (focus_seconds >= 0),
  brief_success_count integer not null default 0 check (brief_success_count >= 0),
  brief_partial_count integer not null default 0 check (brief_partial_count >= 0),
  brief_failed_count integer not null default 0 check (brief_failed_count >= 0),
  brief_cancelled_count integer not null default 0 check (brief_cancelled_count >= 0),
  rest_started_count integer not null default 0 check (rest_started_count >= 0),
  rest_snoozed_count integer not null default 0 check (rest_snoozed_count >= 0),
  rest_skipped_count integer not null default 0 check (rest_skipped_count >= 0),
  updated_at timestamptz not null,
  constraint workday_daily_metric_unique unique (device_id, work_date)
);

create index workday_daily_metrics_device_date_idx
  on workday_daily_metrics(device_id, work_date desc);
