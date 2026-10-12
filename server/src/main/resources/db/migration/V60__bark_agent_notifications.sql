-- Only opaque operation/content fingerprints and delivery receipts, never message bodies or credentials.
-- No foreign keys: the durable outbound marker commits independently of the task's scope locks.
create table bark_notification_attempts (
    operation_key varchar(64) primary key,
    content_sha256 varchar(64) not null,
    receipt_id uuid not null unique,
    status varchar(16) not null check (status in ('SENDING', 'SENT', 'FAILED', 'UNKNOWN')),
    error_code varchar(64),
    started_at timestamptz not null,
    completed_at timestamptz
);
