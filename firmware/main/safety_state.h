#pragma once

typedef enum {
    SAFETY_STATE_MOTION_DISABLED = 0,
} safety_state_t;

void safety_state_init(void);
safety_state_t safety_state_current(void);
void safety_state_stop_motion(void);
const char *safety_state_name(safety_state_t state);
