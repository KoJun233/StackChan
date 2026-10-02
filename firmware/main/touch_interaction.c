#include "touch_interaction.h"
#include <stdlib.h>

bool touch_interaction_face_moved(int16_t sx, int16_t sy, int16_t x, int16_t y)
{
    return abs(x - sx) > 12 || abs(y - sy) > 12;
}

touch_face_gesture_t touch_interaction_face_gesture(touch_interaction_phase_t phase,
    uint32_t held_ms, int16_t sx, int16_t sy, int16_t x, int16_t y)
{
    if (phase != TOUCH_INTERACTION_IDLE || held_ms < 50 || held_ms >= TOUCH_INTERACTION_LONG_PRESS_MS)
        return TOUCH_FACE_GESTURE_NONE;
    int dx = x - sx, dy = y - sy;
    if (abs(dx) >= 40 && abs(dx) >= 2 * abs(dy))
        return dx < 0 ? TOUCH_FACE_GESTURE_PTT : TOUCH_FACE_GESTURE_WAKE;
    if (dy <= -40 && -dy >= 2 * abs(dx)) return TOUCH_FACE_GESTURE_MENU;
    return TOUCH_FACE_GESTURE_NONE;
}

touch_interaction_action_t touch_interaction_capture_press_action(
    touch_interaction_phase_t phase, bool press_to_talk)
{
    if (phase == TOUCH_INTERACTION_LISTENING && !press_to_talk) {
        return TOUCH_INTERACTION_ACTION_NONE;
    }
    return touch_interaction_press_action(phase);
}

touch_interaction_action_t touch_interaction_wake_record_release_action(
    touch_interaction_phase_t phase, uint32_t held_ms, bool moved,
    int16_t sx, int16_t sy, int16_t x, int16_t y)
{
    if (phase != TOUCH_INTERACTION_LISTENING || held_ms < 30 ||
        held_ms >= TOUCH_INTERACTION_LONG_PRESS_MS) return TOUCH_INTERACTION_ACTION_NONE;
    int dx = x-sx, dy = y-sy;
    if (dy >= 40 && dy >= 2*abs(dx)) return TOUCH_INTERACTION_ACTION_CANCEL;
    if (!moved && !touch_interaction_face_moved(sx,sy,x,y)) return TOUCH_INTERACTION_ACTION_SUBMIT;
    return TOUCH_INTERACTION_ACTION_NONE;
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
