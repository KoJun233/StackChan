#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int8_t x;
    int8_t y;
} screensaver_pupil_offset_t;

/** Returns the bounded pupil offset for a cyclic low-frequency screensaver frame. */
screensaver_pupil_offset_t screensaver_motion_offset(size_t frame_index);

/** Returns the number of frames in the deterministic pupil-motion cycle. */
size_t screensaver_motion_frame_count(void);

#ifdef __cplusplus
}
#endif
