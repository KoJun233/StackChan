create table companion_roles (
  id uuid primary key,
  name varchar(80) not null,
  tone varchar(24) not null check (tone in ('WARM', 'CALM', 'LIVELY', 'PROFESSIONAL')),
  reply_length varchar(24) not null check (reply_length in ('SHORT', 'BALANCED', 'DETAILED')),
  proactivity varchar(24) not null check (proactivity in ('RESERVED', 'BALANCED', 'PROACTIVE')),
  background_instructions varchar(4000) not null default '',
  topic_boundaries varchar(2000) not null default '',
  taboos varchar(2000) not null default '',
  is_default boolean not null default false,
  archived_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint companion_roles_name_check check (length(trim(name)) between 1 and 80),
  constraint companion_roles_default_archive_check check (not is_default or archived_at is null)
);

create unique index companion_roles_default_uq on companion_roles(is_default) where is_default;
create index companion_roles_archived_idx on companion_roles(archived_at, updated_at desc, id);

insert into companion_roles (
  id, name, tone, reply_length, proactivity, background_instructions,
  topic_boundaries, taboos, is_default, created_at, updated_at
)
select '00000000-0000-0000-0000-000000000001'::uuid,
       display_name, tone, reply_length, proactivity, '', topic_boundaries, taboos,
       true, updated_at, updated_at
from companion_persona_settings where id = 1;

insert into companion_roles (
  id, name, tone, reply_length, proactivity, background_instructions,
  topic_boundaries, taboos, is_default, created_at, updated_at
)
select '00000000-0000-0000-0000-000000000001'::uuid,
       'StackChan', 'WARM', 'BALANCED', 'BALANCED', '', '', '', true,
       current_timestamp, current_timestamp
where not exists (select 1 from companion_roles where is_default);

create table device_active_roles (
  device_id uuid primary key references devices(id) on delete cascade,
  role_id uuid not null references companion_roles(id) on delete restrict,
  updated_at timestamptz not null
);
insert into device_active_roles(device_id, role_id, updated_at)
select id, '00000000-0000-0000-0000-000000000001'::uuid, current_timestamp from devices;

alter table conversations add column role_id uuid references companion_roles(id) on delete restrict;
update conversations set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table conversations alter column role_id set not null;
create index conversations_role_updated_idx on conversations(role_id, updated_at desc, id desc);

alter table long_term_memories add column role_id uuid references companion_roles(id) on delete restrict;
update long_term_memories set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table long_term_memories alter column role_id set not null;
create index long_term_memories_role_management_idx
  on long_term_memories(role_id, confirmation_status, enabled, updated_at desc, id);

alter table reminders add column role_id uuid references companion_roles(id) on delete restrict;
update reminders set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table reminders alter column role_id set not null;
create index reminders_role_schedule_idx on reminders(role_id, status, scheduled_at, id);

alter table notification_integrations add column role_id uuid references companion_roles(id) on delete restrict;
update notification_integrations set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table notification_integrations alter column role_id set not null;
create index notification_integrations_role_idx on notification_integrations(role_id, created_at desc);

alter table device_voice_conversations add column role_id uuid references companion_roles(id) on delete restrict;
update device_voice_conversations set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table device_voice_conversations alter column role_id set not null;
alter table device_voice_conversations drop constraint device_voice_conversations_pkey;
alter table device_voice_conversations add primary key (device_id, role_id);

alter table proactive_topic_cooldowns add column role_id uuid references companion_roles(id) on delete restrict;
update proactive_topic_cooldowns set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table proactive_topic_cooldowns alter column role_id set not null;
alter table proactive_topic_cooldowns drop constraint proactive_topic_cooldowns_pkey;
alter table proactive_topic_cooldowns add primary key (device_id, role_id, topic_key);
drop index proactive_topic_cooldowns_recent_idx;
create index proactive_topic_cooldowns_recent_idx
  on proactive_topic_cooldowns(device_id, role_id, last_mentioned_at desc, topic_key);

alter table voice_action_proposals add column role_id uuid references companion_roles(id) on delete restrict;
alter table voice_action_proposals drop constraint voice_action_proposal_type_check;
alter table voice_action_proposals add constraint voice_action_proposal_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE'
));
update voice_action_proposals set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table voice_action_proposals alter column role_id set not null;
drop index voice_action_proposals_pending_scope_uq;
create unique index voice_action_proposals_pending_scope_uq
  on voice_action_proposals(actor_id, device_id, role_id, conversation_id) where status = 'PENDING';
create index voice_action_proposals_role_created_idx on voice_action_proposals(role_id, created_at desc, id);

alter table voice_action_audits add column role_id uuid references companion_roles(id) on delete restrict;
alter table voice_action_audits drop constraint voice_action_audit_type_check;
alter table voice_action_audits add constraint voice_action_audit_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE'
));
update voice_action_audits set role_id = '00000000-0000-0000-0000-000000000001'::uuid;
alter table voice_action_audits alter column role_id set not null;
