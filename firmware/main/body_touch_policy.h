#pragma once

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    BODY_TOUCH_NONE = 0,
    BODY_TOUCH_AFFECTION,
    BODY_TOUCH_WORKDAY_TOGGLE,
} body_touch_action_t;

body_touch_action_t body_touch_release_action(uint32_t held_ms, bool used_for_stop,
                                            bool idle_at_press, bool idle_at_release);

#ifdef __cplusplus
}
#endif
