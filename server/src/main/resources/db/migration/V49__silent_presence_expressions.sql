ALTER TABLE device_interaction_settings
  ADD COLUMN silent_presence_enabled boolean NOT NULL DEFAULT false,
  ADD COLUMN silent_presence_next_at timestamp with time zone,
  ADD COLUMN silent_presence_last_at timestamp with time zone,
  ADD COLUMN silent_presence_counter_date date,
  ADD COLUMN silent_presence_counter integer NOT NULL DEFAULT 0
    CHECK (silent_presence_counter BETWEEN 0 AND 8);

CREATE INDEX idx_interaction_silent_presence_due
  ON device_interaction_settings (silent_presence_next_at)
  WHERE silent_presence_enabled = true;
