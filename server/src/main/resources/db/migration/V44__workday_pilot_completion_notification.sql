alter table workday_pilot_observations
  add column completion_notification_queued_at timestamptz;

create unique index reminders_workday_pilot_completion_unique
  on reminders(device_id, proactive_topic_key)
  where source = 'PROACTIVE' and proactive_topic_key like 'workday:pilot:complete:%';
