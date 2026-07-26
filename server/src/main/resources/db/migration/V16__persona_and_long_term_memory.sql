create table companion_persona_settings (
  id smallint primary key check (id = 1),
  display_name varchar(80) not null,
  tone varchar(24) not null check (tone in ('WARM', 'CALM', 'LIVELY', 'PROFESSIONAL')),
  reply_length varchar(24) not null check (reply_length in ('SHORT', 'BALANCED', 'DETAILED')),
  proactivity varchar(24) not null check (proactivity in ('RESERVED', 'BALANCED', 'PROACTIVE')),
  topic_boundaries varchar(2000) not null default '',
  taboos varchar(2000) not null default '',
  updated_at timestamptz not null
);

insert into companion_persona_settings (
  id, display_name, tone, reply_length, proactivity, topic_boundaries, taboos, updated_at
) values (
  1, 'StackChan', 'WARM', 'BALANCED', 'BALANCED', '', '', current_timestamp
);

create table long_term_memories (
  id uuid primary key,
  scope_type varchar(24) not null check (scope_type in ('GLOBAL', 'DEVICE')),
  device_id uuid references devices(id) on delete cascade,
  category varchar(24) not null check (category in ('USER_PROFILE', 'EVENT')),
  title varchar(120) not null,
  content varchar(2000) not null,
  source varchar(32) not null check (source in ('USER_ENTERED', 'ASSISTANT_SUGGESTED')),
  source_detail varchar(500) not null,
  confirmation_status varchar(24) not null check (confirmation_status in ('PENDING', 'CONFIRMED', 'REJECTED')),
  enabled boolean not null,
  confirmed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint long_term_memories_scope_check check (
    (scope_type = 'GLOBAL' and device_id is null)
    or (scope_type = 'DEVICE' and device_id is not null)
  ),
  constraint long_term_memories_enabled_check check (
    enabled = false or confirmation_status = 'CONFIRMED'
  )
);

create index long_term_memories_context_idx
  on long_term_memories(confirmation_status, enabled, scope_type, device_id, updated_at desc);

create index long_term_memories_management_idx
  on long_term_memories(category, confirmation_status, updated_at desc, id);
