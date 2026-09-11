alter table reminders
  add column proactive_source_name varchar(120),
  add column proactive_source_title varchar(300),
  add column proactive_source_url varchar(1000),
  add column proactive_source_published_at timestamptz,
  add column proactive_source_retrieved_at timestamptz;

create index reminders_proactive_source_url_idx
  on reminders(device_id, proactive_source_url)
  where source = 'PROACTIVE' and proactive_source_url is not null;
