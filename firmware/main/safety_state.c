#include "safety_state.h"

#include <string.h>

#include "esp_log.h"
#include "freertos/FreeRTOS.h"

#define SAFETY_MOTION_TIMEOUT_GRACE_US 500000LL

static const char *TAG = "safety_state";
static safety_diagnostics_t s_diagnostics;
static int64_t s_motion_deadline_us;
static safety_motion_stop_callback_t s_stop_callback;
static void *s_stop_callback_context;
static portMUX_TYPE s_lock = portMUX_INITIALIZER_UNLOCKED;

static int64_t motion_timeout_us(safety_motion_template_t motion)
{
    switch (motion) {
    case SAFETY_MOTION_WAKE: return 1800000LL;
    case SAFETY_MOTION_LOOK_USER: return 1600000LL;
    case SAFETY_MOTION_NOD_SMALL: return 2200000LL;
    case SAFETY_MOTION_THINK: return 2400000LL;
    case SAFETY_MOTION_DROWSY: return 2600000LL;
    default: return 0;
    }
}

static void record_failure_locked(safety_failure_code_t failure)
{
    if (failure == SAFETY_FAILURE_NONE) return;
    s_diagnostics.last_failure = failure;
    if (s_diagnostics.failure_count < UINT32_MAX) s_diagnostics.failure_count++;
}

static bool stop_hardware_if_running_locked(void)
{
    bool was_running = s_diagnostics.motion_runtime == SAFETY_MOTION_RUNNING;
    s_diagnostics.motion_runtime = SAFETY_MOTION_IDLE;
    s_motion_deadline_us = 0;
    return was_running;
}

static void invoke_stop_callback(bool should_stop)
{
    safety_motion_stop_callback_t callback = NULL;
    void *context = NULL;
    if (!should_stop) return;
    taskENTER_CRITICAL(&s_lock);
    callback = s_stop_callback;
    context = s_stop_callback_context;
    taskEXIT_CRITICAL(&s_lock);
    if (callback != NULL) callback(context);
}

void safety_state_init(void)
{
    taskENTER_CRITICAL(&s_lock);
    memset(&s_diagnostics, 0, sizeof(s_diagnostics));
    s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
    s_diagnostics.motion_runtime = SAFETY_MOTION_IDLE;
    s_diagnostics.active_template = SAFETY_MOTION_WAKE;
    s_diagnostics.last_failure = SAFETY_FAILURE_NONE;
    s_motion_deadline_us = 0;
    s_stop_callback = NULL;
    s_stop_callback_context = NULL;
    taskEXIT_CRITICAL(&s_lock);
    ESP_LOGI(TAG, "Safety state is motion_disabled");
}

safety_state_t safety_state_current(void)
{
    safety_state_t state;
    taskENTER_CRITICAL(&s_lock);
    state = s_diagnostics.state;
    taskEXIT_CRITICAL(&s_lock);
    return state;
}

void safety_state_set_motion_capabilities(bool body_motion_supported,
                                          bool servo_feedback_supported)
{
    bool should_stop = false;
    taskENTER_CRITICAL(&s_lock);
    s_diagnostics.body_motion_supported = body_motion_supported;
    s_diagnostics.servo_feedback_supported = servo_feedback_supported;
    if (!body_motion_supported || !servo_feedback_supported) {
        should_stop = stop_hardware_if_running_locked();
        s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
    }
    taskEXIT_CRITICAL(&s_lock);
    invoke_stop_callback(should_stop);
}

void safety_state_set_calibrated(bool calibrated)
{
    bool should_stop = false;
    taskENTER_CRITICAL(&s_lock);
    s_diagnostics.calibrated = calibrated;
    if (!calibrated) {
        should_stop = stop_hardware_if_running_locked();
        s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
    }
    taskEXIT_CRITICAL(&s_lock);
    invoke_stop_callback(should_stop);
}

bool safety_state_set_admin_enabled(bool enabled)
{
    bool accepted = false;
    bool should_stop = false;
    taskENTER_CRITICAL(&s_lock);
    if (!enabled) {
        should_stop = stop_hardware_if_running_locked();
        s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
        accepted = true;
    } else if (!s_diagnostics.body_motion_supported || !s_diagnostics.servo_feedback_supported) {
        record_failure_locked(SAFETY_FAILURE_CAPABILITY_MISSING);
    } else if (!s_diagnostics.calibrated) {
        record_failure_locked(SAFETY_FAILURE_NOT_CALIBRATED);
    } else if (s_diagnostics.motion_runtime == SAFETY_MOTION_RUNNING) {
        record_failure_locked(SAFETY_FAILURE_BUSY);
    } else {
        s_diagnostics.state = SAFETY_STATE_MOTION_ARMED;
        accepted = true;
    }
    taskEXIT_CRITICAL(&s_lock);
    invoke_stop_callback(should_stop);
    return accepted;
}

void safety_state_register_stop_callback(safety_motion_stop_callback_t callback,
                                         void *context)
{
    taskENTER_CRITICAL(&s_lock);
    s_stop_callback = callback;
    s_stop_callback_context = context;
    taskEXIT_CRITICAL(&s_lock);
}

bool safety_state_begin_motion(safety_motion_template_t motion,
                               const safety_motion_guard_t *guard,
                               int64_t now_us)
{
    safety_failure_code_t rejection = SAFETY_FAILURE_NONE;
    int64_t timeout_us = motion_timeout_us(motion);
    taskENTER_CRITICAL(&s_lock);
    if (motion < 0 || motion >= SAFETY_MOTION_TEMPLATE_COUNT || timeout_us <= 0 ||
        guard == NULL || now_us < 0) {
        rejection = SAFETY_FAILURE_HARDWARE_FAILURE;
    } else if (!s_diagnostics.body_motion_supported || !s_diagnostics.servo_feedback_supported) {
        rejection = SAFETY_FAILURE_CAPABILITY_MISSING;
    } else if (!s_diagnostics.calibrated) {
        rejection = SAFETY_FAILURE_NOT_CALIBRATED;
    } else if (s_diagnostics.state != SAFETY_STATE_MOTION_ARMED) {
        rejection = SAFETY_FAILURE_ADMIN_DISABLED;
    } else if (!guard->connected) {
        rejection = SAFETY_FAILURE_OFFLINE;
    } else if (guard->audio_busy) {
        rejection = SAFETY_FAILURE_AUDIO_BUSY;
    } else if (guard->updating) {
        rejection = SAFETY_FAILURE_UPDATING;
    } else if (guard->device_error) {
        rejection = SAFETY_FAILURE_DEVICE_ERROR;
    } else if (s_diagnostics.motion_runtime == SAFETY_MOTION_RUNNING) {
        rejection = SAFETY_FAILURE_BUSY;
    }
    if (rejection != SAFETY_FAILURE_NONE) {
        record_failure_locked(rejection);
        taskEXIT_CRITICAL(&s_lock);
        return false;
    }
    s_diagnostics.active_template = motion;
    s_diagnostics.motion_runtime = SAFETY_MOTION_RUNNING;
    s_motion_deadline_us = now_us + timeout_us + SAFETY_MOTION_TIMEOUT_GRACE_US;
    taskEXIT_CRITICAL(&s_lock);
    return true;
}

void safety_state_complete_motion(void)
{
    taskENTER_CRITICAL(&s_lock);
    s_diagnostics.motion_runtime = SAFETY_MOTION_IDLE;
    s_motion_deadline_us = 0;
    taskEXIT_CRITICAL(&s_lock);
}

void safety_state_fail_motion(safety_failure_code_t failure)
{
    bool should_stop;
    if (failure == SAFETY_FAILURE_NONE) failure = SAFETY_FAILURE_HARDWARE_FAILURE;
    taskENTER_CRITICAL(&s_lock);
    should_stop = stop_hardware_if_running_locked();
    s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
    record_failure_locked(failure);
    taskEXIT_CRITICAL(&s_lock);
    invoke_stop_callback(should_stop);
}

void safety_state_tick(int64_t now_us)
{
    bool should_stop = false;
    taskENTER_CRITICAL(&s_lock);
    if (s_diagnostics.motion_runtime == SAFETY_MOTION_RUNNING &&
        s_motion_deadline_us > 0 && now_us >= s_motion_deadline_us) {
        should_stop = stop_hardware_if_running_locked();
        s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
        record_failure_locked(SAFETY_FAILURE_TIMEOUT);
    }
    taskEXIT_CRITICAL(&s_lock);
    invoke_stop_callback(should_stop);
}

void safety_state_stop_motion(void)
{
    bool should_stop;
    taskENTER_CRITICAL(&s_lock);
    should_stop = stop_hardware_if_running_locked();
    s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
    taskEXIT_CRITICAL(&s_lock);
    invoke_stop_callback(should_stop);
}

void safety_state_stop_motion_with_reason(safety_failure_code_t reason)
{
    bool should_stop;
    taskENTER_CRITICAL(&s_lock);
    should_stop = stop_hardware_if_running_locked();
    s_diagnostics.state = SAFETY_STATE_MOTION_DISABLED;
    record_failure_locked(reason);
    taskEXIT_CRITICAL(&s_lock);
    invoke_stop_callback(should_stop);
}

void safety_state_get_diagnostics(safety_diagnostics_t *diagnostics)
{
    if (diagnostics == NULL) return;
    taskENTER_CRITICAL(&s_lock);
    *diagnostics = s_diagnostics;
    taskEXIT_CRITICAL(&s_lock);
}

const char *safety_state_name(safety_state_t state)
{
    return state == SAFETY_STATE_MOTION_ARMED ? "motion_armed" : "motion_disabled";
}

const char *safety_motion_runtime_name(safety_motion_runtime_t runtime)
{
    return runtime == SAFETY_MOTION_RUNNING ? "RUNNING" : "IDLE";
}

const char *safety_motion_template_name(safety_motion_template_t motion)
{
    switch (motion) {
    case SAFETY_MOTION_WAKE: return "WAKE";
    case SAFETY_MOTION_LOOK_USER: return "LOOK_USER";
    case SAFETY_MOTION_NOD_SMALL: return "NOD_SMALL";
    case SAFETY_MOTION_THINK: return "THINK";
    case SAFETY_MOTION_DROWSY: return "DROWSY";
    default: return NULL;
    }
}

bool safety_motion_template_parse(const char *value, safety_motion_template_t *motion)
{
    if (value == NULL || motion == NULL) return false;
    for (int candidate = 0; candidate < SAFETY_MOTION_TEMPLATE_COUNT; candidate++) {
        const char *name = safety_motion_template_name((safety_motion_template_t)candidate);
        if (name != NULL && strcmp(name, value) == 0) {
            *motion = (safety_motion_template_t)candidate;
            return true;
        }
    }
    return false;
}

const char *safety_failure_code_name(safety_failure_code_t failure)
{
    switch (failure) {
    case SAFETY_FAILURE_NONE: return "NONE";
    case SAFETY_FAILURE_CAPABILITY_MISSING: return "CAPABILITY_MISSING";
    case SAFETY_FAILURE_NOT_CALIBRATED: return "NOT_CALIBRATED";
    case SAFETY_FAILURE_ADMIN_DISABLED: return "ADMIN_DISABLED";
    case SAFETY_FAILURE_AUDIO_BUSY: return "AUDIO_BUSY";
    case SAFETY_FAILURE_OFFLINE: return "OFFLINE";
    case SAFETY_FAILURE_UPDATING: return "UPDATING";
    case SAFETY_FAILURE_DEVICE_ERROR: return "DEVICE_ERROR";
    case SAFETY_FAILURE_BUSY: return "BUSY";
    case SAFETY_FAILURE_SOFT_LIMIT: return "SOFT_LIMIT";
    case SAFETY_FAILURE_TIMEOUT: return "TIMEOUT";
    case SAFETY_FAILURE_TOUCH_STOP: return "TOUCH_STOP";
    case SAFETY_FAILURE_VOICE_STOP: return "VOICE_STOP";
    case SAFETY_FAILURE_FEEDBACK_FAULT: return "FEEDBACK_FAULT";
    case SAFETY_FAILURE_HARDWARE_FAILURE: return "HARDWARE_FAILURE";
    default: return "HARDWARE_FAILURE";
    }
}
