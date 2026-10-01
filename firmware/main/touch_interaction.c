#include "touch_interaction.h"

bool touch_interaction_in_submit_area(int16_t x, int16_t y)
{
    return x >= TOUCH_INTERACTION_SUBMIT_X &&
           x < TOUCH_INTERACTION_SUBMIT_X + TOUCH_INTERACTION_SUBMIT_SIZE &&
           y >= TOUCH_INTERACTION_SUBMIT_Y &&
           y < TOUCH_INTERACTION_SUBMIT_Y + TOUCH_INTERACTION_SUBMIT_SIZE;
}

touch_interaction_action_t touch_interaction_capture_press_action(
    touch_interaction_phase_t phase, bool press_to_talk, int16_t x, int16_t y)
{
    if (phase == TOUCH_INTERACTION_LISTENING && !press_to_talk &&
        touch_interaction_in_submit_area(x, y)) {
        return TOUCH_INTERACTION_ACTION_NONE;
    }
    return touch_interaction_press_action(phase);
}

bool touch_interaction_should_toggle_input_mode(touch_interaction_phase_t phase,
                                                bool began_in_mode_area, uint32_t held_ms,
                                                int16_t x, int16_t y)
{
    return phase == TOUCH_INTERACTION_IDLE && began_in_mode_area && held_ms >= 50U &&
           held_ms < TOUCH_INTERACTION_LONG_PRESS_MS && touch_interaction_in_submit_area(x, y);
}

touch_interaction_action_t touch_interaction_capture_release_action(
    touch_interaction_phase_t phase, bool press_to_talk, bool began_in_submit_area,
    int16_t x, int16_t y)
{
    if (phase != TOUCH_INTERACTION_LISTENING || press_to_talk || !began_in_submit_area) {
        return TOUCH_INTERACTION_ACTION_NONE;
    }
    return touch_interaction_in_submit_area(x, y)
               ? TOUCH_INTERACTION_ACTION_SUBMIT : TOUCH_INTERACTION_ACTION_CANCEL;
}

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
