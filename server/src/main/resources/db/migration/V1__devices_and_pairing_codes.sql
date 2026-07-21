CREATE TABLE devices (
    id UUID PRIMARY KEY,
    hardware_id VARCHAR(255) NOT NULL UNIQUE,
    firmware_version VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL DEFAULT 'StackChan',
    last_seen_at TIMESTAMP WITH TIME ZONE,
    safety_state VARCHAR(64) NOT NULL DEFAULT 'motion_disabled'
);

CREATE TABLE pairing_codes (
    id UUID PRIMARY KEY,
    value VARCHAR(255) NOT NULL UNIQUE,
    created_by VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_by_device_id UUID REFERENCES devices(id),
    created_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pairing_codes_consumption_check
        CHECK ((NOT consumed AND consumed_by_device_id IS NULL) OR (consumed AND consumed_by_device_id IS NOT NULL))
);
