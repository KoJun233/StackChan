alter table delegations add column project_loop boolean not null default false;
alter table delegations add column project_source_mode varchar(32) not null default 'TEXT_ONLY'
    check (project_source_mode in ('GIVEN_URLS','READ_ONLY_EXPLORATION','TEXT_ONLY'));
alter table delegations add column cancelled_at timestamptz;
alter table delegations add column cancellation_reason text;

create table delegation_result_reviews (
    run_id uuid primary key references delegation_runs(id) on delete cascade,
    review_state varchar(32) not null default 'UNREVIEWED'
        check (review_state in ('UNREVIEWED','ACCEPTED','REVISION_REQUESTED','SUPERSEDED')),
    resource_version bigint not null default 1 check (resource_version > 0),
    supersedes_run_id uuid references delegation_runs(id) on delete set null,
    acknowledged_at timestamptz
);
create table delegation_result_feedback (
    id uuid primary key,
    task_id uuid not null references delegations(id) on delete cascade,
    run_id uuid not null references delegation_runs(id) on delete cascade,
    device_id uuid not null references devices(id) on delete cascade,
    role_id uuid not null references companion_roles(id) on delete cascade,
    kind varchar(32) not null check (kind in ('ACCEPT_RESULT','REVISE_PREFERENCES','PAUSE','CANCEL')),
    comment text not null check (char_length(comment) <= 2000),
    expected_revision bigint not null check (expected_revision > 0),
    idempotency_key varchar(128) not null,
    created_at timestamptz not null
);
create index delegation_feedback_task_history on delegation_result_feedback(task_id, created_at, id);
create table delegation_revision_intents (
    task_id uuid not null references delegations(id) on delete cascade,
    task_revision bigint not null check (task_revision > 0),
    run_id uuid references delegation_runs(id) on delete set null,
    supersedes_run_id uuid references delegation_runs(id) on delete set null,
    operation_key varchar(128) not null,
    created_at timestamptz not null,
    primary key(task_id, task_revision)
);
-- Durable receipts deliberately contain identifiers and fixed metadata only. No private request/response bodies.
-- No scope FK: deleting private resources must not make their old operation keys executable again.
create table companion_operation_receipts (
    device_id uuid not null,
    role_id uuid not null,
    action varchar(40) not null check (action in ('DELEGATION_CREATE','DELEGATION_FEEDBACK','DELEGATION_RUN_REVISION')),
    operation_key varchar(128) not null,
    resource_id uuid,
    request_digest varchar(64) not null,
    status varchar(16) not null check (status in ('PENDING','COMMITTED','FAILED')),
    result_metadata text,
    error_code varchar(80),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key(device_id,role_id,action,operation_key)
);
