#include "body_local_policy.h"
#include "motion_planner.h"
#include <math.h>
#include <stdlib.h>

bool body_local_follow_goal(float yaw_offset, float pitch_offset, bool recenter, body_local_goal_t *goal)
{
    if (!goal || !isfinite(yaw_offset) || !isfinite(pitch_offset)) return false;
    float yaw = recenter ? 0 : fminf(12, fmaxf(-12, yaw_offset));
    float pitch = recenter ? 0 : fminf(6, fmaxf(0, pitch_offset));
    *goal = (body_local_goal_t){(int)lroundf(yaw), 45 + (int)lroundf(pitch), 600};
    return true;
}

uint16_t body_local_paced_duration(int start_yaw, int start_pitch, int target_yaw,
                                  int target_pitch, uint16_t requested_ms,
                                  uint32_t available_ms)
{
    if (start_yaw < 0 || start_yaw > 1000 || start_pitch < 0 || start_pitch > 1000 ||
        target_yaw < 0 || target_yaw > 1000 || target_pitch < 0 || target_pitch > 1000 ||
        requested_ms == 0) return 0;
    unsigned travel = (unsigned)abs(target_yaw - start_yaw);
    unsigned pitch_travel = (unsigned)abs(target_pitch - start_pitch);
    if (pitch_travel > travel) travel = pitch_travel;
    unsigned duration = (travel * 1875 + MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND - 1) / MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND;
    if (duration < requested_ms) duration = requested_ms;
    return duration <= available_ms && duration <= 6000 ? (uint16_t)duration : 0;
}

uint16_t body_local_frame_duration(int start_yaw, int start_pitch, int target_yaw, int target_pitch)
{
    return body_local_paced_duration(start_yaw, start_pitch, target_yaw, target_pitch, 600, 1200);
}

bool body_local_prepare_paced_frames(int start_yaw, int start_pitch,
                                     body_local_raw_frame_t *frames, size_t count,
                                     uint32_t available_ms, unsigned endpoint_tolerance)
{
    if (!frames || count == 0 || count > 3 || endpoint_tolerance > 16) return false;
    int from_yaw = start_yaw, from_pitch = start_pitch;
    for (size_t i = 0; i < count; ++i) {
        if (i != 0) {
            from_yaw += frames[i].yaw >= from_yaw ? -(int)endpoint_tolerance : (int)endpoint_tolerance;
            from_pitch += frames[i].pitch >= from_pitch ? -(int)endpoint_tolerance : (int)endpoint_tolerance;
            if (from_yaw < 0) from_yaw = 0;
            if (from_yaw > 1000) from_yaw = 1000;
            if (from_pitch < 0) from_pitch = 0;
            if (from_pitch > 1000) from_pitch = 1000;
        }
        uint16_t duration = body_local_paced_duration(from_yaw, from_pitch,
            frames[i].yaw, frames[i].pitch, frames[i].duration_ms, available_ms);
        if (duration == 0) return false;
        frames[i].duration_ms = duration;
        available_ms -= duration;
        from_yaw = frames[i].yaw;
        from_pitch = frames[i].pitch;
    }
    return true;
}

uint16_t body_local_recovery_duration(int observed_pitch, int target_pitch)
{
    unsigned travel = (unsigned)abs(target_pitch - observed_pitch);
    unsigned duration = (travel * 1000 + MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND - 1) / MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND;
    return (uint16_t)(duration < 200 ? 200 : duration);
}

bool body_local_goal_is_near(int yaw, int pitch, int target_yaw, int target_pitch)
{
    return abs(target_yaw - yaw) <= 8 && abs(target_pitch - pitch) <= 8;
}
