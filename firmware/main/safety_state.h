#pragma once

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    SAFETY_STATE_MOTION_DISABLED = 0,
    SAFETY_STATE_MOTION_ARMED,
} safety_state_t;

typedef enum {
    SAFETY_MOTION_IDLE = 0,
    SAFETY_MOTION_RUNNING,
} safety_motion_runtime_t;

typedef enum {
    SAFETY_MOTION_WAKE = 0,
    SAFETY_MOTION_LOOK_USER,
    SAFETY_MOTION_NOD_SMALL,
    SAFETY_MOTION_THINK,
    SAFETY_MOTION_DROWSY,
    SAFETY_MOTION_TEMPLATE_COUNT,
} safety_motion_template_t;

typedef enum {
    SAFETY_FAILURE_NONE = 0,
    SAFETY_FAILURE_CAPABILITY_MISSING,
    SAFETY_FAILURE_NOT_CALIBRATED,
    SAFETY_FAILURE_ADMIN_DISABLED,
    SAFETY_FAILURE_AUDIO_BUSY,
    SAFETY_FAILURE_OFFLINE,
    SAFETY_FAILURE_UPDATING,
    SAFETY_FAILURE_DEVICE_ERROR,
    SAFETY_FAILURE_BUSY,
    SAFETY_FAILURE_SOFT_LIMIT,
    SAFETY_FAILURE_TIMEOUT,
    SAFETY_FAILURE_TOUCH_STOP,
    SAFETY_FAILURE_VOICE_STOP,
    SAFETY_FAILURE_FEEDBACK_FAULT,
    SAFETY_FAILURE_HARDWARE_FAILURE,
} safety_failure_code_t;

typedef struct {
    bool connected;
    bool audio_busy;
    bool updating;
    bool device_error;
} safety_motion_guard_t;

typedef struct {
    safety_state_t state;
    safety_motion_runtime_t motion_runtime;
    safety_motion_template_t active_template;
    safety_failure_code_t last_failure;
    uint32_t failure_count;
    bool calibrated;
    bool body_motion_supported;
    bool servo_feedback_supported;
} safety_diagnostics_t;

typedef void (*safety_motion_stop_callback_t)(void *context);

void safety_state_init(void);
safety_state_t safety_state_current(void);
void safety_state_set_motion_capabilities(bool body_motion_supported,
                                          bool servo_feedback_supported);
void safety_state_set_calibrated(bool calibrated);
bool safety_state_set_admin_enabled(bool enabled);
void safety_state_register_stop_callback(safety_motion_stop_callback_t callback,
                                         void *context);
bool safety_state_begin_motion(safety_motion_template_t motion,
                               const safety_motion_guard_t *guard,
                               int64_t now_us);
void safety_state_complete_motion(void);
void safety_state_fail_motion(safety_failure_code_t failure);
void safety_state_tick(int64_t now_us);
void safety_state_stop_motion(void);
void safety_state_stop_motion_with_reason(safety_failure_code_t reason);
void safety_state_get_diagnostics(safety_diagnostics_t *diagnostics);
const char *safety_state_name(safety_state_t state);
const char *safety_motion_runtime_name(safety_motion_runtime_t runtime);
const char *safety_motion_template_name(safety_motion_template_t motion);
bool safety_motion_template_parse(const char *value, safety_motion_template_t *motion);
const char *safety_failure_code_name(safety_failure_code_t failure);

#ifdef __cplusplus
}
#endif
