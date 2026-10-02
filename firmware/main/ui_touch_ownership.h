#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Pure routing state shared by the UI producer and host sequence tests. LVGL
 * still receives the touch; this only protects the face/voice event queue. */
typedef struct {
    bool pressed;
    bool consumed;
    int16_t last_x;
    int16_t last_y;
    uint32_t last_move_ms;
} ui_touch_ownership_t;

typedef enum {
    UI_TOUCH_ROUTE_NONE = 0,
    UI_TOUCH_ROUTE_PRESS,
    UI_TOUCH_ROUTE_MOVE,
    UI_TOUCH_ROUTE_RELEASE,
} ui_touch_route_t;

static inline ui_touch_route_t ui_touch_ownership_sample(ui_touch_ownership_t *owner,
    bool touched, bool modal, bool screensaver, int16_t x, int16_t y, uint32_t now_ms)
{
    if (owner == NULL) return UI_TOUCH_ROUTE_NONE;
    if (touched && !owner->pressed) {
        owner->pressed = true;
        owner->consumed = modal || screensaver;
        owner->last_x = x;
        owner->last_y = y;
        owner->last_move_ms = now_ms;
        return owner->consumed ? UI_TOUCH_ROUTE_NONE : UI_TOUCH_ROUTE_PRESS;
    }
    if (owner->pressed && modal) owner->consumed = true;
    if (!touched && owner->pressed) {
        bool consumed = owner->consumed;
        owner->pressed = false;
        owner->consumed = false;
        return consumed ? UI_TOUCH_ROUTE_NONE : UI_TOUCH_ROUTE_RELEASE;
    }
    if (touched && !owner->consumed && now_ms - owner->last_move_ms >= 30U) {
        int32_t delta_x = (int32_t)x - owner->last_x;
        int32_t delta_y = (int32_t)y - owner->last_y;
        if (delta_x >= 3 || delta_x <= -3 || delta_y >= 3 || delta_y <= -3) {
            owner->last_x = x;
            owner->last_y = y;
            owner->last_move_ms = now_ms;
            return UI_TOUCH_ROUTE_MOVE;
        }
    }
    return UI_TOUCH_ROUTE_NONE;
}
