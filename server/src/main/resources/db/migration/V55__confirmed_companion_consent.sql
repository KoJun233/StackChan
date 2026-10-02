alter table voice_action_proposals drop constraint voice_action_proposal_type_check;
alter table voice_action_proposals add constraint voice_action_proposal_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER', 'SET_TEMPORARY_DND', 'SET_VOLUME',
  'CREATE_MEMORY_SUGGESTION', 'CONFIRM_MEMORY', 'CREATE_FOLLOW_UP', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION',
  'START_WORKDAY', 'END_WORKDAY', 'START_WORKDAY_REST', 'SNOOZE_WORKDAY_REST',
  'SKIP_WORKDAY_REST_FOR_DAY', 'CREATE_PERSONAL_TASK', 'COMPLETE_PERSONAL_TASK'
));
alter table voice_action_audits drop constraint voice_action_audit_type_check;
alter table voice_action_audits add constraint voice_action_audit_type_check check (action_type in (
  'CREATE_REMINDER', 'SNOOZE_NEXT_REMINDER', 'SKIP_NEXT_REMINDER', 'SET_TEMPORARY_DND', 'SET_VOLUME',
  'CREATE_MEMORY_SUGGESTION', 'CONFIRM_MEMORY', 'CREATE_FOLLOW_UP', 'SWITCH_ROLE',
  'ACKNOWLEDGE_NOTIFICATION', 'SNOOZE_NOTIFICATION', 'COMPLETE_NOTIFICATION',
  'START_WORKDAY', 'END_WORKDAY', 'START_WORKDAY_REST', 'SNOOZE_WORKDAY_REST',
  'SKIP_WORKDAY_REST_FOR_DAY', 'CREATE_PERSONAL_TASK', 'COMPLETE_PERSONAL_TASK'
));
alter table voice_action_proposals add constraint voice_memory_candidate_check check (
  action_type <> 'CONFIRM_MEMORY' or (target_reference is not null and target_at is not null
    and title is not null and content is not null and length(trim(content)) > 0)
);
alter table voice_action_proposals add constraint voice_follow_up_check check (
  action_type <> 'CREATE_FOLLOW_UP' or (scheduled_at is not null and zone_id is not null
    and recurrence_type is not null and recurrence_type = 'NONE'
    and recurrence_interval is not null and recurrence_interval = 1 and target_reference is null
    and content is not null and length(trim(content)) between 1 and 120)
);

alter table reminders drop constraint reminders_source_check;
alter table reminders add constraint reminders_source_check check (source in ('USER', 'PROACTIVE', 'EXTERNAL', 'FOLLOW_UP'));
alter table reminders drop constraint reminders_proactive_metadata_check;
alter table reminders add constraint reminders_proactive_metadata_check check (
  source in ('PROACTIVE', 'FOLLOW_UP') or (proactive_topic_key is null and proactive_generation_status is null)
);
alter table reminders drop constraint reminders_external_metadata_check;
alter table reminders add constraint reminders_external_metadata_check check (
  (source = 'EXTERNAL' and recurrence_type = 'NONE' and notification_integration_id is not null
    and idempotency_key is not null and idempotency_content_hash ~ '^[0-9a-f]{64}$' and expires_at is not null)
  or (source <> 'EXTERNAL' and notification_integration_id is null and idempotency_key is null
    and idempotency_content_hash is null and (expires_at is null or source = 'FOLLOW_UP'))
);
alter table reminders add constraint reminders_follow_up_check check (
  source <> 'FOLLOW_UP' or (recurrence_type = 'NONE' and recurrence_interval = 1
    and expires_at is not null and expires_at > scheduled_at
    and proactive_topic_key is not null and proactive_topic_key like 'follow-up:%'
    and proactive_generation_status is not null and proactive_generation_status = 'FIXED' and delivery_group_id is null
    and proactive_source_name is null and proactive_source_title is null and proactive_source_url is null)
);
create index reminders_follow_up_expiry_idx on reminders(expires_at, id)
  where source = 'FOLLOW_UP' and status = 'PENDING';
