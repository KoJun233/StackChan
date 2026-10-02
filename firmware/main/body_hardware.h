#pragma once

#include <stdbool.h>

#include "device_protocol.h"
#include "esp_err.h"
#include "safety_state.h"

#ifdef __cplusplus
extern "C" {
#endif

/** Probes the K151 body without enabling servo torque or moving either axis. */
esp_err_t body_hardware_init(void);

/** Captures the current passive servo feedback positions as yaw=0 and pitch=45 degrees. */
esp_err_t body_hardware_calibrate_center(void);

/** Applies the explicit administrator motion gate; reboot always starts disabled. */
bool body_hardware_set_motion_enabled(bool enabled);

/** Queues exactly one firmware-owned motion template after all local guards pass. */
bool body_hardware_play_motion(safety_motion_template_t motion,
                               const safety_motion_guard_t *guard,
                               const char *command_id);

/** Firmware-only bounded tracking goals; never exposed to remote command angles. */
bool body_hardware_follow_local(float yaw_offset, float pitch_offset, bool recenter);
bool body_hardware_nod_local(void);
/** Invalidates only queued/active local tracking, retaining independent remote templates. */
void body_hardware_stop_local_follow(void);

typedef enum {
    BODY_MOTION_COMPLETED = 0,
    BODY_MOTION_STOPPED,
    BODY_MOTION_FAILED,
} body_motion_result_status_t;

typedef struct {
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
    safety_motion_template_t motion;
    body_motion_result_status_t status;
    safety_failure_code_t failure;
} body_motion_result_t;

/** Takes one final execution result; a transport ACK only means the command was queued. */
bool body_hardware_take_motion_result(body_motion_result_t *result);
bool body_hardware_peek_motion_result(body_motion_result_t *result);

/** Copies capability and privacy-safe sensor diagnostics for the heartbeat. */
void body_hardware_get_diagnostics(device_body_diagnostics_t *diagnostics);

/** Returns and clears one locally detected top-touch long-press workday toggle. */
bool body_hardware_take_workday_toggle(void);

#ifdef __cplusplus
}
#endif
