#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "esp_err.h"
#include "face_tracking_policy.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    FACE_TRACKING_FAILURE_NONE = 0,
    FACE_TRACKING_FAILURE_UNSUPPORTED,
    FACE_TRACKING_FAILURE_RESOURCE,
    FACE_TRACKING_FAILURE_CAMERA,
    FACE_TRACKING_FAILURE_FRAME,
} face_tracking_failure_t;

typedef struct {
    bool available, enabled, running, face_present, should_recenter;
    face_tracking_failure_t failure;
    float gaze_x, gaze_y; /* Local display data only; never send in telemetry. */
    uint32_t sampled_frames;
    uint32_t last_inference_ms;
} face_tracking_state_t;

/** Initializes default-off state without allocating a worker, camera or model. */
esp_err_t face_tracking_init(void);
/** Explicit local menu switch; first enable lazily reserves the worker.
 * Allocation failure stays disabled and can be retried. Disable invalidates goals immediately. */
bool face_tracking_set_enabled(bool enabled);
/** Caller owns all idle/online/audio/modal/update/DND and local body safety gates. */
void face_tracking_set_runtime_gate(bool allow_capture, bool allow_head_motion);
void face_tracking_get_state(face_tracking_state_t *state);
/** Returns one locally bounded goal only while both runtime gates remain open. */
bool face_tracking_take_head_goal(face_tracking_head_goal_t *goal);

#ifdef __cplusplus
}
#endif
