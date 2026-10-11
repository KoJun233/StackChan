create table delegations (
    id uuid primary key,
    device_id uuid not null references devices(id) on delete cascade,
    role_id uuid not null references companion_roles(id) on delete cascade,
    title varchar(200) not null,
    instruction varchar(4000) not null,
    source_urls text not null,
    schedule_type varchar(16) not null check (schedule_type in ('DAILY','WEEKLY')),
    days_of_week varchar(30) not null,
    local_time time not null,
    zone_id varchar(80) not null,
    max_items integer not null check (max_items between 1 and 10),
    delivery_mode varchar(16) not null check (delivery_mode in ('CONSOLE','BARK')),
    enabled boolean not null default false,
    revision bigint not null default 1,
    next_run_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index delegations_due on delegations(next_run_at) where enabled;
create index delegations_scope on delegations(device_id, role_id, created_at desc);

create table delegation_runs (
    id uuid primary key,
    task_id uuid not null references delegations(id) on delete cascade,
    task_title varchar(200) not null,
    scheduled_date date not null,
    task_revision bigint not null,
    attempt integer not null check (attempt > 0),
    status varchar(16) not null check (status in ('RUNNING','SUCCEEDED','NO_UPDATES','FAILED','CANCELLED')),
    items text not null default '[]',
    source_warnings text not null default '[]',
    error_code varchar(80),
    delivery_status varchar(16) not null default 'NONE'
        check (delivery_status in ('NONE','PENDING','SENDING','SENT','FAILED','UNKNOWN','SKIPPED')),
    delivery_attempts integer not null default 0,
    delivery_next_at timestamptz,
    created_at timestamptz not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    unique(task_id, scheduled_date)
);
create index delegation_runs_delivery_due on delegation_runs(delivery_next_at) where delivery_status = 'FAILED';
create index delegation_runs_history on delegation_runs(task_id, created_at desc);

create table delegation_push_settings (
    id integer primary key check (id = 1),
    server_url varchar(2048) not null default 'https://api.day.app',
    key_ciphertext text,
    key_iv varchar(64),
    enabled boolean not null default false,
    revision bigint not null default 1,
    updated_at timestamptz not null,
    check ((key_ciphertext is null) = (key_iv is null))
);
insert into delegation_push_settings(id, updated_at) values(1, now());
