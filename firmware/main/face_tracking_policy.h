#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define FACE_TRACKING_MAX_CANDIDATES 10
#define FACE_TRACKING_HEAD_INTERVAL_MS 6000
#define FACE_TRACKING_LOST_GRACE_MS 1200

/* This module has no image, identity, hardware or networking access. */
typedef struct {
    float left, top, right, bottom; /* Normalized image coordinates [0, 1]. */
    float score;
} face_tracking_candidate_t;

typedef struct {
    float yaw_offset_deg;
    float pitch_offset_deg;
    bool recenter;
} face_tracking_head_goal_t;

typedef struct {
    float gaze_x, gaze_y; /* Local display coordinates [-1, 1]. */
    bool face_present;
    bool should_recenter;
    bool head_goal_ready;
    face_tracking_head_goal_t head_goal;
} face_tracking_policy_output_t;

typedef struct {
    bool active;
    bool has_target;
    bool recentering;
    uint64_t last_seen_ms, last_update_ms, last_head_ms;
    float target_x, target_y;
    float gaze_x, gaze_y;
    float last_head_yaw, last_head_pitch;
} face_tracking_policy_t;

void face_tracking_policy_init(face_tracking_policy_t *policy);
void face_tracking_policy_reset(face_tracking_policy_t *policy);
face_tracking_policy_output_t face_tracking_policy_update(
    face_tracking_policy_t *policy, bool capture_allowed, bool head_allowed,
    const face_tracking_candidate_t *faces, size_t count, uint64_t now_ms);

#ifdef __cplusplus
}
#endif
