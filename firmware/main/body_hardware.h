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
                               const safety_motion_guard_t *guard);

/** Copies capability and privacy-safe sensor diagnostics for the heartbeat. */
void body_hardware_get_diagnostics(device_body_diagnostics_t *diagnostics);

/** Returns and clears one locally detected top-touch long-press workday toggle. */
bool body_hardware_take_workday_toggle(void);

#ifdef __cplusplus
}
#endif
