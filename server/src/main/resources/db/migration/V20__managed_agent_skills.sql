create table agent_skills (
  id uuid primary key,
  name varchar(64) not null unique,
  description varchar(1024) not null,
  version varchar(64),
  directory_name varchar(64) not null unique,
  content_sha256 char(64) not null,
  enabled boolean not null,
  file_count integer not null,
  uncompressed_bytes bigint not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint agent_skill_name_check check (
    name ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
  ),
  constraint agent_skill_file_count_check check (file_count > 0),
  constraint agent_skill_uncompressed_bytes_check check (uncompressed_bytes > 0)
);

create index agent_skills_updated_idx on agent_skills(updated_at desc);
