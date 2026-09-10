ALTER TABLE device_interaction_settings
  ADD COLUMN proactive_next_at timestamptz;

UPDATE device_interaction_settings
SET proactive_min_interval_minutes = GREATEST(proactive_min_interval_minutes, 60),
    proactive_daily_limit = LEAST(proactive_daily_limit, 3);
