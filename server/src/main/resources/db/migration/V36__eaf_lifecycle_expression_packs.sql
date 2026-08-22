alter table expression_packs drop constraint expression_pack_format_check;
alter table expression_packs add column pack_type varchar(24) not null default 'STATIC_PNG';
alter table expression_packs add constraint expression_pack_format_check check (format_version in (1, 2));
alter table expression_packs add constraint expression_pack_type_check check (
  (pack_type = 'STATIC_PNG' and format_version = 1)
  or (pack_type = 'LIFECYCLE_EAF' and format_version = 2)
);

create table expression_pack_clips (
  pack_id uuid not null references expression_packs(id) on delete cascade,
  clip_name varchar(32) not null,
  clip_sha256 varchar(64) not null,
  clip_size integer not null,
  frame_count integer not null,
  frame_delay_ms integer not null,
  clip_data bytea not null,
  primary key (pack_id, clip_name),
  constraint expression_pack_clip_name_check check (clip_name in ('boot_appear', 'wake', 'role_switch')),
  constraint expression_pack_clip_size_check check (clip_size > 0 and clip_size <= 393216),
  constraint expression_pack_clip_frames_check check (frame_count between 1 and 120),
  constraint expression_pack_clip_delay_check check (frame_delay_ms between 16 and 100),
  constraint expression_pack_clip_duration_check check (frame_count * frame_delay_ms <= 5000)
);

alter table devices add column lifecycle_clip_supported boolean not null default false;
