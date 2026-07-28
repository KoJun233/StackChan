create table expression_packs (
  id uuid primary key,
  name varchar(80) not null,
  description varchar(240),
  format_version integer not null,
  artifact_sha256 varchar(64) not null,
  artifact_size integer not null,
  artifact bytea not null,
  created_at timestamptz not null,
  constraint expression_pack_format_check check (format_version = 1),
  constraint expression_pack_size_check check (artifact_size > 0 and artifact_size <= 1572864)
);

create table expression_pack_states (
  pack_id uuid not null references expression_packs(id) on delete cascade,
  state_name varchar(32) not null,
  image_sha256 varchar(64) not null,
  image_size integer not null,
  image_data bytea not null,
  primary key (pack_id, state_name),
  constraint expression_pack_state_name_check check (
    state_name in ('idle', 'listening', 'processing', 'speaking', 'success', 'no_speech', 'offline', 'error')
  ),
  constraint expression_pack_state_size_check check (image_size > 0 and image_size <= 393216)
);

create table device_expression_packs (
  device_id uuid primary key references devices(id) on delete cascade,
  pack_id uuid references expression_packs(id) on delete restrict,
  enabled boolean not null default false,
  status varchar(24) not null,
  command_id varchar(96),
  failure_code varchar(80),
  updated_at timestamptz not null,
  installed_at timestamptz,
  constraint device_expression_pack_status_check check (
    status in ('READY', 'INSTALLING', 'ACTIVE', 'FAILED', 'DISABLED')
  ),
  constraint device_expression_pack_enabled_check check (
    (enabled and pack_id is not null and status in ('READY', 'INSTALLING', 'ACTIVE', 'FAILED'))
    or (not enabled and pack_id is null and status = 'DISABLED')
  )
);

create unique index device_expression_packs_command_uq
  on device_expression_packs(command_id) where command_id is not null;

create index device_expression_packs_dispatch_idx
  on device_expression_packs(status, updated_at);
