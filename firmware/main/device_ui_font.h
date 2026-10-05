#pragma once
#include "lvgl.h"

#ifdef __cplusplus
extern "C" {
#endif
const lv_font_t *device_ui_font(void);
/** Grayscale fixed-control fonts with complete CJK fallback. */
const lv_font_t *device_menu_font(void);
const lv_font_t *device_number_font(void);
/** A confirmation must never silently substitute missing characters. */
bool device_ui_font_covers_text(const char *text);
#ifdef __cplusplus
}
#endif
