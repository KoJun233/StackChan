create table personal_tasks (
  id uuid primary key,
  device_id uuid not null references devices(id) on delete cascade,
  role_id uuid not null references companion_roles(id) on delete restrict,
  title varchar(200) not null,
  notes varchar(2000),
  priority varchar(16) not null check (priority in ('LOW', 'NORMAL', 'HIGH')),
  status varchar(16) not null check (status in ('OPEN', 'COMPLETED')),
  due_at timestamptz,
  zone_id varchar(80) not null,
  reminder_id uuid unique references reminders(id) on delete set null,
  completed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint personal_tasks_completion_check check (
    (status = 'OPEN' and completed_at is null) or
    (status = 'COMPLETED' and completed_at is not null)
  )
);

create index personal_tasks_role_status_due_idx
  on personal_tasks(role_id, status, due_at, created_at desc, id desc);

create index personal_tasks_device_role_status_idx
  on personal_tasks(device_id, role_id, status, created_at desc, id desc);

alter table voice_action_proposals drop constraint voice_action_proposal_type_check;
alter table voice_action_proposals add constraint voice_action_proposal_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION',
  'START_WORKDAY', 'END_WORKDAY', 'START_WORKDAY_REST',
  'SNOOZE_WORKDAY_REST', 'SKIP_WORKDAY_REST_FOR_DAY',
  'CREATE_PERSONAL_TASK', 'COMPLETE_PERSONAL_TASK'
));

alter table voice_action_audits drop constraint voice_action_audit_type_check;
alter table voice_action_audits add constraint voice_action_audit_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION',
  'START_WORKDAY', 'END_WORKDAY', 'START_WORKDAY_REST',
  'SNOOZE_WORKDAY_REST', 'SKIP_WORKDAY_REST_FOR_DAY',
  'CREATE_PERSONAL_TASK', 'COMPLETE_PERSONAL_TASK'
));
