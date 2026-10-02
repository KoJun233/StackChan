alter table voice_action_proposals add column source_role_name varchar(80);
update voice_action_proposals proposal set source_role_name = role.name
from companion_roles role where role.id = proposal.role_id;
alter table voice_action_proposals alter column source_role_name set not null;

-- A switch away and back must not resurrect consent from the previous active-partner period.
alter table device_active_roles add column consent_epoch uuid not null default gen_random_uuid();
alter table voice_action_proposals add column source_consent_epoch uuid;
update voice_action_proposals proposal set source_consent_epoch = active.consent_epoch
from device_active_roles active where active.device_id = proposal.device_id and active.role_id = proposal.role_id;
update voice_action_proposals proposal set source_consent_epoch = '00000000-0000-0000-0000-000000000000'
where proposal.role_id = '00000000-0000-0000-0000-000000000001'
and not exists(select 1 from device_active_roles active where active.device_id = proposal.device_id);

-- Older task/role proposals lack the fixed object snapshot required by the new confirmation protocol.
-- Retire only those pending proposals, preserving completed history and the old firmware's other actions.
update voice_action_proposals set status = 'EXPIRED', updated_at = now()
where status = 'PENDING' and (
  (action_type = 'COMPLETE_PERSONAL_TASK' and (target_reference is null or target_at is null))
  or (action_type = 'SWITCH_ROLE' and target_reference is null)
);
-- Do not assign today's epoch to an older proposal after a pre-upgrade partner change.
update voice_action_proposals proposal set status = 'EXPIRED', updated_at = now()
where proposal.status = 'PENDING' and exists (
  select 1 from device_active_roles active where active.device_id = proposal.device_id
  and (active.role_id <> proposal.role_id or active.updated_at > proposal.created_at)
);
alter table voice_action_proposals add constraint voice_action_pending_object_snapshot_check check (
  status <> 'PENDING'
  or (action_type not in ('COMPLETE_PERSONAL_TASK', 'SWITCH_ROLE'))
  or (action_type = 'COMPLETE_PERSONAL_TASK' and target_reference is not null and target_at is not null
    and content is not null and zone_id is not null)
  or (action_type = 'SWITCH_ROLE' and target_reference is not null and content is not null)
);
