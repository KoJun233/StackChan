#pragma once

#include "expression_engine.h"
#include "lvgl.h"

#ifdef __cplusplus
extern "C" {
#endif

/* LVGL owns the object and renderer storage. Call only with the LVGL/board
 * mutex held. Parent deletion releases storage; no timers or global state.
 * The default face surface is 160 x 160, with a solid black background. */
lv_obj_t *robot_eyes_renderer_create(lv_obj_t *parent);
void robot_eyes_renderer_update(lv_obj_t *eyes,
                                const companion_expression_pose_t *pose,
                                uint32_t theme_rgb);
void robot_eyes_renderer_set_status(lv_obj_t *eyes, companion_face_state_t state,
                                    bool updating, bool wake_mode);

#ifdef __cplusplus
}
#endif
