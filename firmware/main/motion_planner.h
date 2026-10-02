#pragma once

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MOTION_PLAN_PERIOD_MS 25U
#define MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND 192U

typedef struct {
    int16_t start_yaw;
    int16_t start_pitch;
    int16_t target_yaw;
    int16_t target_pitch;
    uint32_t duration_ms;
} motion_plan_t;

typedef struct {
    int16_t yaw;
    int16_t pitch;
    uint32_t deadline_ms;
    uint16_t goal_time_ms;
    bool final;
} motion_plan_sample_t;

/** Raw endpoints are already subject to the hardware's calibrated soft limits.
 * Reject invalid feedback and trajectories exceeding the local speed budget. */
bool motion_plan_init(motion_plan_t *plan, int start_yaw, int start_pitch,
                      int target_yaw, int target_pitch, uint32_t duration_ms);

/** Samples the next absolute boundary from elapsed time. A late caller skips
 * obsolete boundaries; it never emits a burst of old goals to catch up. */
bool motion_plan_sample(const motion_plan_t *plan, uint32_t elapsed_ms,
                        motion_plan_sample_t *sample);

/** Extends an individual servo goal when a delayed update spans more travel,
 * keeping the same speed bound even after an I2C or scheduling stall. */
uint16_t motion_plan_goal_time(const motion_plan_sample_t *sample,
                              int previous_yaw, int previous_pitch);

#ifdef __cplusplus
}
#endif
