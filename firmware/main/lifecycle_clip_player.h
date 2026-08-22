#pragma once

#include <stdbool.h>

#include "lvgl.h"

#include "expression_pack.h"

#ifdef __cplusplus
extern "C" {
#endif

void lifecycle_clip_player_init(lv_obj_t *screen, int x, int y);
bool lifecycle_clip_player_play(expression_lifecycle_clip_t clip);
void lifecycle_clip_player_stop(void);
void lifecycle_clip_player_poll(void);
bool lifecycle_clip_player_is_active(void);

#ifdef __cplusplus
}
#endif
