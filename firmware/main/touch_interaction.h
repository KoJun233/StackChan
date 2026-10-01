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
    TOUCH_INTERACTION_ACTION_SUBMIT,
} touch_interaction_action_t;

#define TOUCH_INTERACTION_LONG_PRESS_MS 600U
#define TOUCH_INTERACTION_SUBMIT_X 256
#define TOUCH_INTERACTION_SUBMIT_Y 184
#define TOUCH_INTERACTION_SUBMIT_SIZE 48

bool touch_interaction_in_submit_area(int16_t x, int16_t y);
bool touch_interaction_should_toggle_input_mode(touch_interaction_phase_t phase,
                                                bool began_in_mode_area, uint32_t held_ms,
                                                int16_t x, int16_t y);
touch_interaction_action_t touch_interaction_capture_press_action(
    touch_interaction_phase_t phase, bool press_to_talk, int16_t x, int16_t y);
touch_interaction_action_t touch_interaction_capture_release_action(
    touch_interaction_phase_t phase, bool press_to_talk, bool began_in_submit_area,
    int16_t x, int16_t y);

bool touch_interaction_should_start_press_to_talk(touch_interaction_phase_t phase,
                                                  uint32_t held_ms,
                                                  bool online,
                                                  bool already_started);

/** Returns the immediate action for a touch press in the current interaction phase. */
touch_interaction_action_t touch_interaction_press_action(touch_interaction_phase_t phase);

touch_interaction_action_t touch_interaction_release_action(touch_interaction_phase_t phase,
                                                            uint32_t held_ms);
