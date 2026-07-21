ALTER TABLE devices
    ADD COLUMN refresh_token_hash VARCHAR(64),
    ADD COLUMN refresh_token_issued_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
