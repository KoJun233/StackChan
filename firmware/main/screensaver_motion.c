#include "screensaver_motion.h"

static const screensaver_pupil_offset_t PUPIL_OFFSETS[] = {
    {0, 0},
    {-7, -3},
    {-3, -7},
    {6, -5},
    {8, 1},
    {4, 6},
    {-4, 7},
    {-8, 2},
};

screensaver_pupil_offset_t screensaver_motion_offset(size_t frame_index)
{
    return PUPIL_OFFSETS[frame_index % screensaver_motion_frame_count()];
}

size_t screensaver_motion_frame_count(void)
{
    return sizeof(PUPIL_OFFSETS) / sizeof(PUPIL_OFFSETS[0]);
}
