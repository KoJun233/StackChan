alter table delegations drop constraint delegations_schedule_type_check;
alter table delegations add constraint delegations_schedule_type_check
    check (schedule_type in ('ONCE','DAILY','WEEKLY','INTERVAL'));
alter table delegations add column understanding varchar(4000) not null default '';
alter table delegations add column requires_tool_evidence boolean not null default true;
alter table delegations add column allowed_tool_names text not null default '[]';
alter table delegations add column required_tool_names text not null default '[]';
alter table delegations add column once_at timestamptz;
alter table delegations add column interval_minutes integer;
alter table delegations add constraint delegations_interval_check
    check (interval_minutes is null or interval_minutes between 1 and 525600);
update delegations set understanding = instruction;

alter table delegation_runs drop constraint delegation_runs_task_id_scheduled_date_key;
alter table delegation_runs drop constraint delegation_runs_status_check;
alter table delegation_runs add constraint delegation_runs_status_check
    check (status in ('RUNNING','SUCCEEDED','NO_UPDATES','FAILED','CANCELLED','BLOCKED'));
alter table delegation_runs add column occurrence_key varchar(100);
alter table delegation_runs add column scheduled_at timestamptz;
alter table delegation_runs add column output text not null default '';
alter table delegation_runs add column steps text not null default '[]';
alter table delegation_runs add column evidence text not null default '[]';
update delegation_runs set occurrence_key = 'local:' || scheduled_date::text,
    scheduled_at = started_at;
alter table delegation_runs alter column occurrence_key set not null;
alter table delegation_runs alter column scheduled_at set not null;
alter table delegation_runs add constraint delegation_runs_occurrence_unique unique(task_id, occurrence_key);

alter table agent_tool_invocations drop constraint agent_tool_channel_check;
alter table agent_tool_invocations add constraint agent_tool_channel_check
    check (channel in ('WEB','VOICE','DELEGATION'));
alter table agent_tool_invocations add column delegation_run_id uuid;
alter table agent_tool_invocations add column delegation_attempt integer;
create index agent_tool_invocations_delegation_run_idx
    on agent_tool_invocations(delegation_run_id, delegation_attempt, created_at);
