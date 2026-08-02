alter table device_interaction_settings
  add column temporary_dnd_until timestamptz;

create table voice_action_proposals (
  id uuid primary key,
  actor_id varchar(64) not null,
  device_id uuid not null references devices(id) on delete cascade,
  conversation_id uuid not null references conversations(id) on delete cascade,
  source_turn_id uuid not null,
  action_type varchar(40) not null,
  status varchar(24) not null,
  confirmation_required boolean not null,
  content varchar(2000),
  title varchar(120),
  scheduled_at timestamptz,
  zone_id varchar(80),
  recurrence_type varchar(16),
  recurrence_interval integer,
  duration_minutes integer,
  target_at timestamptz,
  volume_percent integer,
  memory_category varchar(24),
  result_reference uuid,
  failure_code varchar(64),
  expires_at timestamptz not null,
  executed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint voice_action_proposal_type_check check (action_type in (
    'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
    'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION'
  )),
  constraint voice_action_proposal_status_check check (status in (
    'PENDING', 'EXECUTING', 'EXECUTED', 'CANCELLED', 'EXPIRED', 'FAILED'
  )),
  constraint voice_action_proposal_expiry_check check (expires_at > created_at),
  constraint voice_action_proposal_interval_check check (
    recurrence_interval is null or recurrence_interval between 1 and 365
  ),
  constraint voice_action_proposal_duration_check check (
    duration_minutes is null or duration_minutes between 1 and 1440
  ),
  constraint voice_action_proposal_volume_check check (
    volume_percent is null or volume_percent between 0 and 100
  ),
  constraint voice_action_proposal_recurrence_check check (
    recurrence_type is null or recurrence_type in ('NONE', 'DAILY', 'WEEKLY')
  ),
  constraint voice_action_proposal_memory_category_check check (
    memory_category is null or memory_category in ('USER_PROFILE', 'EVENT')
  ),
  constraint voice_action_proposal_confirmation_check check (
    (action_type = 'CREATE_MEMORY_SUGGESTION' and not confirmation_required)
    or (action_type <> 'CREATE_MEMORY_SUGGESTION' and confirmation_required)
  )
);

create unique index voice_action_proposals_pending_scope_uq
  on voice_action_proposals(actor_id, device_id, conversation_id)
  where status = 'PENDING';

create index voice_action_proposals_scope_created_idx
  on voice_action_proposals(actor_id, device_id, conversation_id, created_at desc);

create index voice_action_proposals_expiry_idx
  on voice_action_proposals(status, expires_at);

create table voice_action_audits (
  id uuid primary key,
  proposal_id uuid not null references voice_action_proposals(id) on delete cascade,
  actor_id varchar(64) not null,
  device_id uuid references devices(id) on delete set null,
  conversation_id uuid,
  turn_id uuid not null,
  action_type varchar(40) not null,
  event_type varchar(24) not null,
  failure_code varchar(64),
  created_at timestamptz not null,
  constraint voice_action_audit_type_check check (action_type in (
    'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
    'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION'
  )),
  constraint voice_action_audit_event_check check (event_type in (
    'PROPOSED', 'CONFIRMED', 'EXECUTED', 'CANCELLED', 'EXPIRED', 'FAILED'
  ))
);

create index voice_action_audits_created_idx
  on voice_action_audits(created_at desc, id);

create index voice_action_audits_proposal_idx
  on voice_action_audits(proposal_id, created_at);
