create extension if not exists pg_trgm;

alter table long_term_memories
  add column topic_key varchar(120),
  add column importance smallint not null default 3,
  add column last_used_at timestamptz,
  add column source_turn_id uuid,
  add column replaces_memory_id uuid references long_term_memories(id) on delete set null,
  add column superseded_by_memory_id uuid references long_term_memories(id) on delete set null,
  add column allow_proactive_mention boolean not null default false;

update long_term_memories
set topic_key = lower(trim(title))
where topic_key is null;

alter table long_term_memories
  alter column topic_key set not null,
  add constraint long_term_memories_topic_key_check check (length(trim(topic_key)) between 1 and 120),
  add constraint long_term_memories_importance_check check (importance between 1 and 5),
  add constraint long_term_memories_replacement_check check (
    (replaces_memory_id is null or replaces_memory_id <> id)
    and (superseded_by_memory_id is null or superseded_by_memory_id <> id)
  );

create unique index long_term_memories_source_turn_suggestion_uidx
  on long_term_memories(source_turn_id)
  where source = 'ASSISTANT_SUGGESTED' and source_turn_id is not null;

create index long_term_memories_topic_scope_idx
  on long_term_memories(topic_key, scope_type, device_id, confirmation_status, enabled);

create index long_term_memories_retrieval_idx
  on long_term_memories(confirmation_status, enabled, scope_type, device_id, importance desc, updated_at desc)
  where superseded_by_memory_id is null;

create index long_term_memories_trgm_idx
  on long_term_memories using gin (
    (topic_key || ' ' || title || ' ' || content) gin_trgm_ops
  );

create table memory_usage_records (
  turn_id uuid not null,
  memory_id uuid not null references long_term_memories(id) on delete cascade,
  used_at timestamptz not null,
  primary key (turn_id, memory_id)
);

create index memory_usage_records_memory_idx
  on memory_usage_records(memory_id, used_at desc);
