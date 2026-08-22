ALTER TABLE devices
    ADD COLUMN expression_fps_mode VARCHAR(16) NOT NULL DEFAULT 'ADAPTIVE',
    ADD COLUMN expression_min_fps INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN expression_max_fps INTEGER NOT NULL DEFAULT 60;

ALTER TABLE devices
    ADD CONSTRAINT chk_devices_expression_fps_mode
        CHECK (expression_fps_mode IN ('FIXED', 'ADAPTIVE')),
    ADD CONSTRAINT chk_devices_expression_min_fps
        CHECK (expression_min_fps IN (20, 30, 60)),
    ADD CONSTRAINT chk_devices_expression_max_fps
        CHECK (expression_max_fps IN (20, 30, 60)),
    ADD CONSTRAINT chk_devices_expression_fps_range
        CHECK (expression_min_fps <= expression_max_fps),
    ADD CONSTRAINT chk_devices_expression_fixed_fps
        CHECK (expression_fps_mode <> 'FIXED' OR expression_min_fps = expression_max_fps);
