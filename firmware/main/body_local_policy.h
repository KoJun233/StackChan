#pragma once
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif

typedef struct { int yaw_degrees, pitch_degrees; uint16_t duration_ms; } body_local_goal_t;
bool body_local_follow_goal(float yaw_offset, float pitch_offset, bool recenter, body_local_goal_t *goal);
/** Reserve feedback/settle/recovery time inside the existing LOOK_USER deadline. */
uint16_t body_local_frame_duration(int start_yaw, int start_pitch, int target_yaw, int target_pitch);
/** Stretch a paced segment to the same speed bound, or reject its remaining budget. */
uint16_t body_local_paced_duration(int start_yaw, int start_pitch, int target_yaw,
                                  int target_pitch, uint16_t requested_ms,
                                  uint32_t available_ms);
typedef struct { int yaw, pitch; uint16_t duration_ms; } body_local_raw_frame_t;
/** Admit all frames before any goal, including valid prior endpoint error. */
bool body_local_prepare_paced_frames(int start_yaw, int start_pitch,
                                     body_local_raw_frame_t *frames, size_t count,
                                     uint32_t available_ms, unsigned endpoint_tolerance);
uint16_t body_local_recovery_duration(int observed_pitch, int target_pitch);
bool body_local_goal_is_near(int yaw, int pitch, int target_yaw, int target_pitch);
#ifdef __cplusplus
}
#endif
