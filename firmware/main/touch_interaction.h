#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef enum {
    TOUCH_INTERACTION_IDLE = 0,
    TOUCH_INTERACTION_LISTENING,
    TOUCH_INTERACTION_PROCESSING,
    TOUCH_INTERACTION_PLAYING,
    TOUCH_INTERACTION_FEEDBACK,
    TOUCH_INTERACTION_BUSY,
} touch_interaction_phase_t;

typedef enum {
    TOUCH_INTERACTION_ACTION_NONE = 0,
    TOUCH_INTERACTION_ACTION_CANCEL,
    TOUCH_INTERACTION_ACTION_DISMISS,
} touch_interaction_action_t;

#define TOUCH_INTERACTION_LONG_PRESS_MS 600U

bool touch_interaction_should_start_press_to_talk(touch_interaction_phase_t phase,
                                                  uint32_t held_ms,
                                                  bool online,
                                                  bool already_started);

touch_interaction_action_t touch_interaction_release_action(touch_interaction_phase_t phase,
                                                            uint32_t held_ms);
