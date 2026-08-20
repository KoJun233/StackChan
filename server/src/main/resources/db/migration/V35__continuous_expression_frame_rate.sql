ALTER TABLE devices
    DROP CONSTRAINT chk_devices_expression_target_fps,
    DROP CONSTRAINT chk_devices_expression_min_fps,
    DROP CONSTRAINT chk_devices_expression_max_fps;

ALTER TABLE devices
    ADD CONSTRAINT chk_devices_expression_target_fps
        CHECK (expression_target_fps IS NULL OR expression_target_fps BETWEEN 1 AND 60),
    ADD CONSTRAINT chk_devices_expression_min_fps
        CHECK (expression_min_fps BETWEEN 1 AND 60),
    ADD CONSTRAINT chk_devices_expression_max_fps
        CHECK (expression_max_fps BETWEEN 1 AND 60);
