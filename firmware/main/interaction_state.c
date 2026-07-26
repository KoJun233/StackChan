#include "interaction_state.h"

static bool is_valid_state(companion_face_state_t state)
{
    return state >= COMPANION_FACE_IDLE && state <= COMPANION_FACE_RECOVERABLE_ERROR;
}

companion_face_state_t companion_interaction_visible_state(companion_face_state_t requested,
                                                            bool connected)
{
    if (!is_valid_state(requested)) {
        return COMPANION_FACE_RECOVERABLE_ERROR;
    }
    if (!connected && (requested == COMPANION_FACE_IDLE || requested == COMPANION_FACE_OFFLINE)) {
        return COMPANION_FACE_OFFLINE;
    }
    return requested;
}

bool companion_interaction_allows_screensaver(companion_face_state_t visible)
{
    return visible == COMPANION_FACE_IDLE || visible == COMPANION_FACE_OFFLINE;
}
