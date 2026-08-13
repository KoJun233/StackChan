alter table notification_integrations
  add column digest_window_seconds integer not null default 0;

alter table notification_integrations
  add constraint notification_integrations_digest_window_check check (
    digest_window_seconds = 0 or digest_window_seconds between 5 and 300
  );

alter table reminders add column delivery_group_id uuid;

alter table reminders
  add constraint reminders_delivery_group_source_check check (
    delivery_group_id is null or source = 'EXTERNAL'
  );

alter table reminders
  add constraint reminders_delivery_group_fk
  foreign key (delivery_group_id) references reminders(id) on delete restrict;

create index reminders_external_delivery_group_idx
  on reminders(delivery_group_id, status, id)
  where delivery_group_id is not null;

create index reminders_external_digest_candidates_idx
  on reminders(notification_integration_id, scheduled_at, id)
  where source = 'EXTERNAL' and status = 'PENDING' and delivery_group_id is null;
