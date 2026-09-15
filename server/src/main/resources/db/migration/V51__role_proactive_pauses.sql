CREATE TABLE role_proactive_pauses (
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES companion_roles(id) ON DELETE CASCADE,
    paused_until TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (device_id, role_id)
);
