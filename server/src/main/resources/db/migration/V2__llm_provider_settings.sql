CREATE TABLE llm_provider_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    base_url VARCHAR(2048) NOT NULL,
    model VARCHAR(160) NOT NULL,
    system_prompt TEXT NOT NULL DEFAULT '',
    api_key_ciphertext TEXT NOT NULL,
    api_key_iv VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
