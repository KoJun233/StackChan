#include "face_animation.h"

#include <stddef.h>

typedef struct {
    int8_t x;
    int8_t y;
} gaze_point_t;

static const gaze_point_t IDLE_GAZE[] = {
    {0, 0},
    {-4, -2},
    {-2, -3},
    {0, 0},
    {4, -1},
    {2, 2},
    {0, 0},
};

static uint8_t triangle_wave(uint32_t elapsed_ms, uint32_t period_ms)
{
    uint32_t half = period_ms / 2;
    uint32_t phase = elapsed_ms % period_ms;
    uint32_t value = phase <= half ? phase : period_ms - phase;
    return (uint8_t)((value * 100U) / half);
}

static uint8_t blink_openness(uint32_t elapsed_ms)
{
    uint32_t phase = elapsed_ms % 4200U;
    if (phase < 3920U) {
        return 100;
    }
    if (phase < 3990U) {
        return 58;
    }
    if (phase < 4060U) {
        return 12;
    }
    if (phase < 4130U) {
        return 58;
    }
    return 100;
}

companion_face_frame_t companion_face_animation_frame(companion_face_state_t state,
                                                        uint32_t elapsed_ms)
{
    companion_face_frame_t frame = {
        .gaze_x = 0,
        .gaze_y = 0,
        .eye_open_percent = 100,
        .mouth_open_percent = 0,
        .activity_percent = 0,
    };

    switch (state) {
        case COMPANION_FACE_IDLE: {
            size_t index = (elapsed_ms / 900U) % (sizeof(IDLE_GAZE) / sizeof(IDLE_GAZE[0]));
            frame.gaze_x = IDLE_GAZE[index].x;
            frame.gaze_y = IDLE_GAZE[index].y;
            frame.eye_open_percent = blink_openness(elapsed_ms);
            break;
        }
        case COMPANION_FACE_LISTENING:
            frame.eye_open_percent = 100;
            frame.activity_percent = triangle_wave(elapsed_ms, 900U);
            frame.gaze_y = -2;
            break;
        case COMPANION_FACE_PROCESSING: {
            static const int8_t PROCESSING_GAZE[] = {-8, -4, 0, 4, 8, 4, 0, -4};
            frame.gaze_x = PROCESSING_GAZE[(elapsed_ms / 180U) %
                                           (sizeof(PROCESSING_GAZE) / sizeof(PROCESSING_GAZE[0]))];
            frame.eye_open_percent = 72;
            frame.activity_percent = triangle_wave(elapsed_ms, 720U);
            break;
        }
        case COMPANION_FACE_SPEAKING:
            frame.eye_open_percent = blink_openness(elapsed_ms + 1100U);
            frame.mouth_open_percent = 24U +
                                       (uint8_t)((triangle_wave(elapsed_ms, 420U) * 76U) / 100U);
            frame.activity_percent = triangle_wave(elapsed_ms, 420U);
            break;
        case COMPANION_FACE_SUCCESS:
            frame.eye_open_percent = 64;
            frame.activity_percent = triangle_wave(elapsed_ms, 800U);
            break;
        case COMPANION_FACE_NO_SPEECH:
            frame.eye_open_percent = 54;
            frame.gaze_y = 4;
            break;
        case COMPANION_FACE_OFFLINE:
            frame.eye_open_percent = 30;
            frame.gaze_y = 3;
            break;
        case COMPANION_FACE_RECOVERABLE_ERROR:
        default:
            frame.eye_open_percent = 78;
            frame.activity_percent = triangle_wave(elapsed_ms, 680U);
            break;
    }
    return frame;
}

bool companion_face_animation_is_dynamic(companion_face_state_t state)
{
    return state == COMPANION_FACE_IDLE ||
           state == COMPANION_FACE_LISTENING ||
           state == COMPANION_FACE_PROCESSING ||
           state == COMPANION_FACE_SPEAKING ||
           state == COMPANION_FACE_SUCCESS ||
           state == COMPANION_FACE_RECOVERABLE_ERROR;
}
