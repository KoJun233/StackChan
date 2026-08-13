alter table reminders add column response_actions varchar(64);
alter table reminders add constraint reminders_response_actions_check check (
  response_actions is null or response_actions in (
    'ACKNOWLEDGE', 'SNOOZE', 'COMPLETE',
    'ACKNOWLEDGE,SNOOZE', 'ACKNOWLEDGE,COMPLETE', 'SNOOZE,COMPLETE',
    'ACKNOWLEDGE,SNOOZE,COMPLETE'
  )
);

create table notification_responses (
  id uuid primary key,
  notification_id uuid not null references reminders(id) on delete cascade,
  notification_integration_id uuid not null references notification_integrations(id) on delete cascade,
  action varchar(24) not null,
  snooze_minutes integer,
  created_at timestamptz not null,
  constraint notification_responses_action_check
    check (action in ('ACKNOWLEDGE', 'SNOOZE', 'COMPLETE')),
  constraint notification_responses_snooze_check
    check ((action = 'SNOOZE' and snooze_minutes between 1 and 1440)
      or (action <> 'SNOOZE' and snooze_minutes is null))
);

create index notification_responses_notification_created_idx
  on notification_responses(notification_id, created_at desc, id desc);

create index notification_responses_integration_created_idx
  on notification_responses(notification_integration_id, created_at desc, id desc);

alter table voice_action_proposals add column target_reference uuid;
alter table voice_action_proposals drop constraint voice_action_proposal_type_check;
alter table voice_action_proposals add constraint voice_action_proposal_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION'
));

alter table voice_action_audits drop constraint voice_action_audit_type_check;
alter table voice_action_audits add constraint voice_action_audit_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION'
));
