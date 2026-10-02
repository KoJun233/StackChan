#include "body_touch_policy.h"

body_touch_action_t body_touch_release_action(uint32_t held_ms, bool used_for_stop,
                                            bool idle_at_press, bool idle_at_release)
{
    if (used_for_stop) return BODY_TOUCH_NONE;
    if (held_ms >= 1500U) return BODY_TOUCH_WORKDAY_TOGGLE;
    return held_ms >= 50U && idle_at_press && idle_at_release
               ? BODY_TOUCH_AFFECTION : BODY_TOUCH_NONE;
}
