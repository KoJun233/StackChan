create table notification_integrations (
  id uuid primary key,
  name varchar(120) not null,
  device_id uuid not null references devices(id) on delete restrict,
  enabled boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint notification_integrations_name_check check (length(trim(name)) between 1 and 120)
);

create table notification_integration_tokens (
  id uuid primary key,
  integration_id uuid not null references notification_integrations(id) on delete cascade,
  token_hash varchar(64) not null unique,
  expires_at timestamptz,
  revoked_at timestamptz,
  last_used_at timestamptz,
  created_at timestamptz not null,
  constraint notification_token_hash_check check (token_hash ~ '^[0-9a-f]{64}$'),
  constraint notification_token_expiry_check check (expires_at is null or expires_at > created_at)
);

create index notification_tokens_integration_idx
  on notification_integration_tokens(integration_id, created_at desc);

alter table reminders
  add column notification_integration_id uuid references notification_integrations(id) on delete restrict,
  add column idempotency_key varchar(128),
  add column idempotency_content_hash varchar(64),
  add column expires_at timestamptz;

alter table reminders drop constraint if exists reminders_source_check;
alter table reminders
  add constraint reminders_source_check check (source in ('USER', 'PROACTIVE', 'EXTERNAL'));

alter table reminders drop constraint if exists reminders_status_check;
alter table reminders
  add constraint reminders_status_check check (
    status in ('PENDING', 'DISPATCHED', 'DELIVERED', 'FAILED', 'CANCELLED', 'SKIPPED', 'EXPIRED')
  );

alter table reminders drop constraint if exists reminders_last_outcome_check;
alter table reminders
  add constraint reminders_last_outcome_check check (
    last_outcome is null or last_outcome in ('DELIVERED', 'FAILED', 'CANCELLED', 'SKIPPED', 'EXPIRED')
  );

alter table reminders
  add constraint reminders_external_metadata_check check (
    (source = 'EXTERNAL'
      and notification_integration_id is not null
      and idempotency_key is not null
      and idempotency_content_hash ~ '^[0-9a-f]{64}$'
      and expires_at is not null)
    or
    (source <> 'EXTERNAL'
      and notification_integration_id is null
      and idempotency_key is null
      and idempotency_content_hash is null
      and expires_at is null)
  );

create unique index reminders_external_idempotency_uq
  on reminders(notification_integration_id, idempotency_key)
  where notification_integration_id is not null;

create index reminders_external_queue_idx
  on reminders(notification_integration_id, status, created_at desc)
  where source = 'EXTERNAL';

create index reminders_external_expiry_idx
  on reminders(expires_at, id)
  where source = 'EXTERNAL' and status = 'PENDING';

-- Older schedulers could dispatch more than one reminder for a device. Keep the
-- oldest dispatch active and safely return the remainder to the pending queue
-- before enforcing device-level single flight.
with ranked_dispatches as (
  select id,
         row_number() over (partition by device_id order by updated_at, id) as dispatch_rank
  from reminders
  where status = 'DISPATCHED'
)
update reminders
set status = 'PENDING',
    command_id = null,
    audio_payload = null,
    failure_code = null,
    updated_at = now()
where id in (
  select id
  from ranked_dispatches
  where dispatch_rank > 1
);

create unique index reminders_device_dispatched_uq
  on reminders(device_id)
  where status = 'DISPATCHED';
