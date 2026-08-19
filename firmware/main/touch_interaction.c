#include "touch_interaction.h"

bool touch_interaction_should_start_press_to_talk(touch_interaction_phase_t phase,
                                                  uint32_t held_ms,
                                                  bool online,
                                                  bool already_started)
{
    return phase == TOUCH_INTERACTION_IDLE && online && !already_started &&
           held_ms >= TOUCH_INTERACTION_LONG_PRESS_MS;
}

touch_interaction_action_t touch_interaction_press_action(touch_interaction_phase_t phase)
{
    if (phase == TOUCH_INTERACTION_LISTENING || phase == TOUCH_INTERACTION_PROCESSING ||
        phase == TOUCH_INTERACTION_PLAYING) {
        return TOUCH_INTERACTION_ACTION_CANCEL;
    }
    return TOUCH_INTERACTION_ACTION_NONE;
}

touch_interaction_action_t touch_interaction_release_action(touch_interaction_phase_t phase,
                                                            uint32_t held_ms)
{
    if (held_ms >= TOUCH_INTERACTION_LONG_PRESS_MS) {
        return TOUCH_INTERACTION_ACTION_NONE;
    }
    if (phase == TOUCH_INTERACTION_LISTENING || phase == TOUCH_INTERACTION_PROCESSING ||
        phase == TOUCH_INTERACTION_PLAYING) {
        return TOUCH_INTERACTION_ACTION_CANCEL;
    }
    if (phase == TOUCH_INTERACTION_FEEDBACK) {
        return TOUCH_INTERACTION_ACTION_DISMISS;
    }
    return TOUCH_INTERACTION_ACTION_NONE;
}
