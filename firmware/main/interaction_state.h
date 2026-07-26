#pragma once

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    COMPANION_FACE_IDLE = 0,
    COMPANION_FACE_LISTENING,
    COMPANION_FACE_PROCESSING,
    COMPANION_FACE_SPEAKING,
    COMPANION_FACE_SUCCESS,
    COMPANION_FACE_NO_SPEECH,
    COMPANION_FACE_OFFLINE,
    COMPANION_FACE_RECOVERABLE_ERROR,
} companion_face_state_t;

/** Resolves connectivity and the current interaction phase into one visible state. */
companion_face_state_t companion_interaction_visible_state(companion_face_state_t requested,
                                                            bool connected);

/** Limits the low-frequency pupil screensaver to passive states. */
bool companion_interaction_allows_screensaver(companion_face_state_t visible);

#ifdef __cplusplus
}
#endif
