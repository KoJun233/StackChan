alter table voice_action_proposals drop constraint voice_action_proposal_type_check;
alter table voice_action_proposals add constraint voice_action_proposal_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION',
  'START_WORKDAY', 'END_WORKDAY', 'START_WORKDAY_REST',
  'SNOOZE_WORKDAY_REST', 'SKIP_WORKDAY_REST_FOR_DAY'
));

alter table voice_action_audits drop constraint voice_action_audit_type_check;
alter table voice_action_audits add constraint voice_action_audit_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER',
  'SET_TEMPORARY_DND', 'SET_VOLUME', 'CREATE_MEMORY_SUGGESTION', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION',
  'START_WORKDAY', 'END_WORKDAY', 'START_WORKDAY_REST',
  'SNOOZE_WORKDAY_REST', 'SKIP_WORKDAY_REST_FOR_DAY'
));

create unique index reminders_workday_brief_date_unique
  on reminders(device_id, substring(proactive_topic_key from 1 for 24))
  where source = 'PROACTIVE' and proactive_topic_key like 'workday:brief:%';

create unique index reminders_workday_rest_topic_unique
  on reminders(device_id, proactive_topic_key)
  where source = 'PROACTIVE' and proactive_topic_key like 'workday:rest:%';
