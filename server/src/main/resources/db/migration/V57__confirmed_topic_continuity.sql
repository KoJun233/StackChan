-- Ordinary memories do not gain permission to continue a topic during upgrade.
alter table long_term_memories
  add column continuation_state varchar(24) not null default 'NONE',
  add column continuation_progress_at timestamptz,
  add column continuation_paused_until timestamptz,
  add column continuation_offered_at timestamptz,
  add column continuation_discussed_at timestamptz,
  add column continuation_conversation_id uuid references conversations(id) on delete set null,
  add column continuation_progress_conversation_id uuid references conversations(id) on delete set null,
  add column continuation_progress varchar(240) not null default '';

alter table long_term_memories add constraint memory_continuation_state_check check (
  continuation_state in ('NONE', 'PENDING', 'ACTIVE', 'PAUSED', 'STOPPED', 'DORMANT')
);
alter table long_term_memories add constraint memory_continuation_scope_check check (
  continuation_state = 'NONE' or (category = 'EVENT' and scope_type = 'DEVICE' and device_id is not null)
);
create index memory_continuation_lookup on long_term_memories(role_id, device_id, updated_at desc)
  where continuation_state <> 'NONE' and superseded_by_memory_id is null;

-- Generated suggestions and visible offers have independent daily caps; deleting a
-- candidate must not reset either cap. No titles, contents or dialogue bodies here.
create table continuation_daily_budget (
  device_id uuid not null references devices(id) on delete cascade,
  local_date date not null,
  stage varchar(16) not null check (stage in ('GENERATED', 'OFFERED')),
  primary key (device_id, local_date, stage)
);
