create table agent_mcp_connections (
  id uuid primary key,
  connection_name varchar(64) not null unique,
  url varchar(2048) not null,
  endpoint varchar(512) not null,
  auth_type varchar(16) not null,
  bearer_token_ciphertext text,
  bearer_token_iv varchar(64),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint agent_mcp_connection_name_check check (
    connection_name ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
  ),
  constraint agent_mcp_auth_type_check check (auth_type in ('NONE', 'BEARER')),
  constraint agent_mcp_bearer_pair_check check (
    (auth_type = 'NONE' and bearer_token_ciphertext is null and bearer_token_iv is null)
    or
    (auth_type = 'BEARER' and bearer_token_ciphertext is not null and bearer_token_iv is not null)
  )
);

create index agent_mcp_connections_updated_idx on agent_mcp_connections(updated_at desc);
