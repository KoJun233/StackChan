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

typedef enum {
    TOUCH_FACE_GESTURE_NONE = 0,
    TOUCH_FACE_GESTURE_PTT,
    TOUCH_FACE_GESTURE_WAKE,
    TOUCH_FACE_GESTURE_MENU,
} touch_face_gesture_t;
/** Only an idle, short, deliberate swipe may choose a mode or open the menu. */
touch_face_gesture_t touch_interaction_face_gesture(touch_interaction_phase_t phase,
    uint32_t held_ms, int16_t start_x, int16_t start_y, int16_t end_x, int16_t end_y);
bool touch_interaction_face_moved(int16_t start_x, int16_t start_y, int16_t x, int16_t y);
/** WAKE recording: deliberate short tap submits; downward swipe cancels. */
touch_interaction_action_t touch_interaction_wake_record_release_action(
    touch_interaction_phase_t phase, uint32_t held_ms, bool moved,
    int16_t start_x, int16_t start_y, int16_t end_x, int16_t end_y);

touch_interaction_action_t touch_interaction_capture_press_action(
    touch_interaction_phase_t phase, bool press_to_talk);

bool touch_interaction_should_start_press_to_talk(touch_interaction_phase_t phase,
                                                  uint32_t held_ms,
                                                  bool online,
                                                  bool already_started);

/** Returns the immediate action for a touch press in the current interaction phase. */
touch_interaction_action_t touch_interaction_press_action(touch_interaction_phase_t phase);

touch_interaction_action_t touch_interaction_release_action(touch_interaction_phase_t phase,
                                                            uint32_t held_ms);
