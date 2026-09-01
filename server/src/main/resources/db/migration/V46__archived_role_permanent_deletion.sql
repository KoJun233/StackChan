-- Archived roles may be permanently deleted only through the guarded service endpoint.
-- Keep device_active_roles restrictive as a final safety check; archiving must move every
-- device back to the default role before this cascade can begin.

alter table conversations drop constraint conversations_role_id_fkey;
alter table conversations add constraint conversations_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table long_term_memories drop constraint long_term_memories_role_id_fkey;
alter table long_term_memories add constraint long_term_memories_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table reminders drop constraint reminders_role_id_fkey;
alter table reminders add constraint reminders_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table notification_integrations drop constraint notification_integrations_role_id_fkey;
alter table notification_integrations add constraint notification_integrations_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table device_voice_conversations drop constraint device_voice_conversations_role_id_fkey;
alter table device_voice_conversations add constraint device_voice_conversations_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table proactive_topic_cooldowns drop constraint proactive_topic_cooldowns_role_id_fkey;
alter table proactive_topic_cooldowns add constraint proactive_topic_cooldowns_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table voice_action_proposals drop constraint voice_action_proposals_role_id_fkey;
alter table voice_action_proposals add constraint voice_action_proposals_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table voice_action_audits drop constraint voice_action_audits_role_id_fkey;
alter table voice_action_audits add constraint voice_action_audits_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

alter table personal_tasks drop constraint personal_tasks_role_id_fkey;
alter table personal_tasks add constraint personal_tasks_role_id_fkey
  foreign key (role_id) references companion_roles(id) on delete cascade;

-- These links otherwise block deletion of the role-owned parent rows.
alter table reminders drop constraint reminders_notification_integration_id_fkey;
alter table reminders add constraint reminders_notification_integration_id_fkey
  foreign key (notification_integration_id) references notification_integrations(id) on delete cascade;

alter table reminders drop constraint reminders_delivery_group_fk;
alter table reminders add constraint reminders_delivery_group_fk
  foreign key (delivery_group_id) references reminders(id) on delete set null;
