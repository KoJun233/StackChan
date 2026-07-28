#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "interaction_state.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int8_t gaze_x;
    int8_t gaze_y;
    uint8_t eye_open_percent;
    uint8_t mouth_open_percent;
    uint8_t activity_percent;
} companion_face_frame_t;

/** Builds a deterministic animation frame for the built-in expressive face. */
companion_face_frame_t companion_face_animation_frame(companion_face_state_t state,
                                                        uint32_t elapsed_ms);

/** Returns whether a state benefits from periodic redraw outside the screensaver. */
bool companion_face_animation_is_dynamic(companion_face_state_t state);

#ifdef __cplusplus
}
#endif
