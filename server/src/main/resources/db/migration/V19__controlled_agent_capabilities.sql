create table agent_runtime_settings (
  id smallint primary key,
  enabled boolean not null,
  updated_at timestamptz not null,
  constraint agent_runtime_settings_singleton_check check (id = 1)
);

insert into agent_runtime_settings(id, enabled, updated_at)
values (1, true, current_timestamp);

create table agent_capability_settings (
  id uuid primary key,
  capability_type varchar(32) not null,
  capability_id varchar(240) not null,
  enabled boolean not null,
  source_id varchar(120),
  schema_sha256 varchar(64),
  updated_at timestamptz not null,
  constraint agent_capability_type_check check (
    capability_type in ('BUILTIN_TOOL', 'SKILL', 'MCP_SERVER', 'MCP_TOOL')
  ),
  constraint agent_capability_schema_check check (
    (capability_type = 'MCP_TOOL' and (not enabled or schema_sha256 is not null))
    or (capability_type <> 'MCP_TOOL' and schema_sha256 is null)
  ),
  unique (capability_type, capability_id)
);

create table agent_tool_invocations (
  id uuid primary key,
  turn_id uuid not null,
  conversation_id uuid,
  device_id uuid references devices(id) on delete set null,
  channel varchar(16) not null,
  skill_id varchar(64),
  tool_name varchar(240) not null,
  source_type varchar(24) not null,
  source_id varchar(120),
  outcome varchar(32) not null,
  duration_ms bigint not null,
  result_bytes integer not null,
  truncated boolean not null,
  created_at timestamptz not null,
  constraint agent_tool_channel_check check (channel in ('WEB', 'VOICE')),
  constraint agent_tool_source_check check (source_type in ('BUILTIN', 'SKILL', 'MCP')),
  constraint agent_tool_outcome_check check (
    outcome in ('SUCCESS', 'TOOL_FAILED', 'RESULT_TRUNCATED', 'RESULT_BUDGET_EXCEEDED')
  ),
  constraint agent_tool_duration_check check (duration_ms >= 0),
  constraint agent_tool_result_bytes_check check (result_bytes >= 0)
);

create index agent_tool_invocations_created_idx
  on agent_tool_invocations(created_at desc);

create index agent_tool_invocations_turn_idx
  on agent_tool_invocations(turn_id, created_at);
