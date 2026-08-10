alter table devices
  add column rssi integer,
  add column application_ota_supported boolean not null default false;

create table firmware_releases (
  id uuid primary key,
  version varchar(32) not null,
  project_name varchar(32) not null,
  artifact_sha256 char(64) not null unique,
  artifact_size integer not null,
  artifact bytea not null,
  created_at timestamptz not null,
  constraint firmware_release_size_check check (artifact_size between 256 and 3145728),
  constraint firmware_release_version_check check (version ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$')
);

create table firmware_update_jobs (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  release_id uuid not null references firmware_releases(id) on delete restrict,
  from_version varchar(80) not null,
  target_version varchar(32) not null,
  status varchar(24) not null check (status in (
    'READY', 'INSTALLING', 'INSTALLED', 'FAILED', 'ROLLED_BACK'
  )),
  command_id varchar(96),
  command_accepted boolean,
  failure_code varchar(80),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  completed_at timestamptz
);

create index firmware_update_jobs_device_idx
  on firmware_update_jobs(device_id, created_at desc);

create index firmware_update_jobs_status_idx
  on firmware_update_jobs(status, updated_at, created_at);

create unique index firmware_update_jobs_command_uq
  on firmware_update_jobs(command_id) where command_id is not null;

create unique index firmware_update_jobs_active_device_uq
  on firmware_update_jobs(device_id) where status in ('READY', 'INSTALLING');
