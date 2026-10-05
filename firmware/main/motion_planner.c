#include "motion_planner.h"

#include <math.h>
#include <stddef.h>
#include <stdlib.h>

bool motion_plan_init(motion_plan_t *plan, int start_yaw, int start_pitch,
                      int target_yaw, int target_pitch, uint32_t duration_ms)
{
    if (plan == NULL || duration_ms == 0 || duration_ms > 6000U ||
        start_yaw < 0 || start_yaw > 1000 || start_pitch < 0 || start_pitch > 1000 ||
        target_yaw < 0 || target_yaw > 1000 || target_pitch < 0 || target_pitch > 1000)
        return false;
    // Quintic easing peaks at 1.875 times the average velocity. Reject a large
    // passive start offset rather than moving it rapidly to the first frame.
    uint32_t travel = (uint32_t)abs(target_yaw - start_yaw);
    uint32_t pitch_travel = (uint32_t)abs(target_pitch - start_pitch);
    if (pitch_travel > travel) travel = pitch_travel;
    if (travel * 1875U > MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND * duration_ms)
        return false;
    *plan = (motion_plan_t){(int16_t)start_yaw, (int16_t)start_pitch,
        (int16_t)target_yaw, (int16_t)target_pitch, duration_ms};
    return true;
}

bool motion_plan_sample(const motion_plan_t *plan, uint32_t elapsed_ms,
                        motion_plan_sample_t *sample)
{
    if (plan == NULL || sample == NULL || plan->duration_ms == 0) return false;
    uint32_t boundary = plan->duration_ms;
    if (elapsed_ms < plan->duration_ms) {
        boundary = (elapsed_ms / MOTION_PLAN_PERIOD_MS + 1U) * MOTION_PLAN_PERIOD_MS;
        if (boundary > plan->duration_ms) boundary = plan->duration_ms;
    }
    float t = (float)boundary / (float)plan->duration_ms;
    float eased = t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    sample->yaw = (int16_t)lroundf(plan->start_yaw +
                                 (plan->target_yaw - plan->start_yaw) * eased);
    sample->pitch = (int16_t)lroundf(plan->start_pitch +
                                   (plan->target_pitch - plan->start_pitch) * eased);
    sample->deadline_ms = boundary;
    sample->goal_time_ms = boundary > elapsed_ms ? (uint16_t)(boundary - elapsed_ms) : 1U;
    sample->final = boundary == plan->duration_ms;
    return true;
}

uint16_t motion_plan_goal_time(const motion_plan_sample_t *sample,
                              int previous_yaw, int previous_pitch)
{
    if (sample == NULL) return 0;
    unsigned int travel = (unsigned int)abs(sample->yaw - previous_yaw);
    unsigned int pitch_travel = (unsigned int)abs(sample->pitch - previous_pitch);
    if (pitch_travel > travel) travel = pitch_travel;
    unsigned int duration = (travel * 1000U + MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND - 1U) /
                            MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND;
    if (duration < sample->goal_time_ms) duration = sample->goal_time_ms;
    return duration == 0 ? 1U : (uint16_t)duration;
}
