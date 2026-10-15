-- Legacy timing records cannot be reliably attributed; keep their association unknown.
ALTER TABLE voice_turns
    ADD COLUMN conversation_id UUID REFERENCES conversations(id) ON DELETE CASCADE;

CREATE INDEX idx_voice_turns_conversation ON voice_turns(conversation_id);

-- Retain only anonymous erased turn IDs for the existing seven-day diagnostic horizon.
CREATE TABLE voice_turn_privacy_tombstones (
    turn_id UUID PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_voice_turn_privacy_tombstones_expiry ON voice_turn_privacy_tombstones(expires_at);
