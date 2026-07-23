create table wake_word_model_jobs (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  phrase varchar(80) not null,
  status varchar(24) not null check (status in (
    'QUEUED', 'GENERATING', 'READY', 'INSTALLING', 'INSTALLED', 'FAILED', 'ROLLED_BACK'
  )),
  model_name varchar(32),
  artifact_sha256 char(64),
  artifact_size integer,
  artifact bytea,
  command_id varchar(96),
  command_accepted boolean,
  failure_code varchar(80),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  installed_at timestamptz,
  constraint wake_word_model_artifact_fields check (
    (status in ('READY', 'INSTALLING')
      and artifact is not null
      and artifact_sha256 is not null
      and artifact_size between 1 and 1048576
      and model_name is not null)
    or
    (status not in ('READY', 'INSTALLING') and artifact is null)
  )
);

create index wake_word_model_jobs_device_idx
  on wake_word_model_jobs(device_id, created_at desc);

create index wake_word_model_jobs_status_idx
  on wake_word_model_jobs(status, updated_at, created_at);

create unique index wake_word_model_jobs_command_uq
  on wake_word_model_jobs(command_id) where command_id is not null;

create unique index wake_word_model_jobs_active_device_uq
  on wake_word_model_jobs(device_id)
  where status in ('QUEUED', 'GENERATING', 'READY', 'INSTALLING');
