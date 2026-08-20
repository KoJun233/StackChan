ALTER TABLE companion_roles
    ADD COLUMN expression_theme_color varchar(7) NOT NULL DEFAULT '#FF4FA3';

ALTER TABLE companion_roles
    ADD CONSTRAINT chk_companion_roles_expression_theme_color
        CHECK (expression_theme_color ~ '^#[0-9A-F]{6}$');

ALTER TABLE devices
    ADD COLUMN dynamic_expression_supported boolean NOT NULL DEFAULT false,
    ADD COLUMN expression_target_fps smallint,
    ADD COLUMN expression_actual_fps smallint,
    ADD COLUMN expression_draw_time_us integer,
    ADD COLUMN expression_transfer_time_us integer,
    ADD COLUMN expression_display_lock_wait_us integer,
    ADD COLUMN expression_dropped_frames bigint,
    ADD COLUMN expression_audio_underruns bigint,
    ADD COLUMN expression_minimum_free_heap bigint,
    ADD COLUMN expression_active_layer varchar(16),
    ADD COLUMN expression_degrade_reason varchar(32),
    ADD COLUMN expression_dynamic_renderer boolean NOT NULL DEFAULT false,
    ADD COLUMN expression_imu_supported boolean NOT NULL DEFAULT false,
    ADD COLUMN expression_proximity_supported boolean NOT NULL DEFAULT false;

ALTER TABLE devices
    ADD CONSTRAINT chk_devices_expression_target_fps
        CHECK (expression_target_fps IS NULL OR expression_target_fps IN (20, 30, 60)),
    ADD CONSTRAINT chk_devices_expression_actual_fps
        CHECK (expression_actual_fps IS NULL OR expression_actual_fps BETWEEN 0 AND 120),
    ADD CONSTRAINT chk_devices_expression_active_layer
        CHECK (expression_active_layer IS NULL OR expression_active_layer IN ('IDLE', 'EMOTION', 'INTERACTION', 'PHYSICAL', 'SYSTEM')),
    ADD CONSTRAINT chk_devices_expression_degrade_reason
        CHECK (expression_degrade_reason IS NULL OR expression_degrade_reason IN ('NONE', 'DRAW_BUDGET', 'DISPLAY_LOCK', 'AUDIO_BUSY', 'AUDIO_UNDERRUN', 'IDLE_SLEEP'));
