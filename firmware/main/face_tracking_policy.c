#include "face_tracking_policy.h"

#include <float.h>
#include <math.h>
#include <string.h>

static float clamp(float value, float low, float high)
{
    return fminf(high, fmaxf(low, value));
}

static float approach(float previous, float target, float limit)
{
    return previous + clamp(target - previous, -limit, limit);
}

void face_tracking_policy_init(face_tracking_policy_t *policy)
{
    if (policy) memset(policy, 0, sizeof(*policy));
}

void face_tracking_policy_reset(face_tracking_policy_t *policy)
{
    face_tracking_policy_init(policy);
}

static bool usable(const face_tracking_candidate_t *face)
{
    return isfinite(face->left) && isfinite(face->top) && isfinite(face->right) &&
           isfinite(face->bottom) && isfinite(face->score) && face->score >= 0.7f &&
           face->left >= 0 && face->top >= 0 && face->right <= 1 && face->bottom <= 1 &&
           face->right - face->left >= 0.05f && face->bottom - face->top >= 0.05f;
}

face_tracking_policy_output_t face_tracking_policy_update(
    face_tracking_policy_t *policy, bool capture_allowed, bool head_allowed,
    const face_tracking_candidate_t *faces, size_t count, uint64_t now_ms)
{
    face_tracking_policy_output_t out = {0};
    if (!policy) return out;
    if (!capture_allowed) {
        face_tracking_policy_reset(policy);
        return out;
    }
    if (!policy->active || now_ms < policy->last_update_ms) {
        face_tracking_policy_reset(policy);
        policy->active = true;
        policy->last_update_ms = policy->last_head_ms = now_ms;
    }
    /* Bound a late or stalled observation: it must not jump the eyes or head. */
    const float dt = clamp((float)(now_ms - policy->last_update_ms) / 1000.0f, 0, 0.5f);
    policy->last_update_ms = now_ms;
    const face_tracking_candidate_t *selected = NULL;
    float best = policy->has_target ? FLT_MAX : 0;
    if (!faces) count = 0;
    if (count > FACE_TRACKING_MAX_CANDIDATES) count = FACE_TRACKING_MAX_CANDIDATES;
    for (size_t i = 0; i < count; ++i) {
        if (!usable(&faces[i])) continue;
        float x = faces[i].left + faces[i].right - 1;
        float y = faces[i].top + faces[i].bottom - 1;
        if (policy->has_target) {
            float dx = x - policy->target_x, dy = y - policy->target_y;
            float distance = dx * dx + dy * dy;
            /* Position continuity, never identity recognition. Do not jump to a passerby. */
            if (distance < best && distance <= 0.65f * 0.65f) {
                best = distance;
                selected = &faces[i];
            }
        } else {
            float area = (faces[i].right - faces[i].left) * (faces[i].bottom - faces[i].top);
            if (area > best) {
                best = area;
                selected = &faces[i];
            }
        }
    }
    if (selected) {
        policy->has_target = true;
        policy->recentering = false;
        policy->target_x = selected->left + selected->right - 1;
        policy->target_y = selected->top + selected->bottom - 1;
        policy->last_seen_ms = now_ms;
    }
    uint64_t lost_ms = policy->has_target ? now_ms - policy->last_seen_ms : UINT64_MAX;
    out.face_present = policy->has_target && lost_ms < FACE_TRACKING_LOST_GRACE_MS;
    if (policy->has_target && lost_ms >= FACE_TRACKING_LOST_GRACE_MS) policy->recentering = true;
    out.should_recenter = policy->recentering;
    float target_x = out.face_present ? policy->target_x : 0;
    float target_y = out.face_present ? policy->target_y : 0;
    /* A small central dead zone avoids idle micro-movements. Short loss holds the gaze. */
    if (fabsf(target_x) < 0.12f) target_x = 0;
    if (fabsf(target_y) < 0.12f) target_y = 0;
    float alpha = dt / (0.20f + dt);
    float speed = out.face_present ? 1.8f : 0.8f;
    policy->gaze_x = approach(policy->gaze_x, policy->gaze_x + alpha * (target_x - policy->gaze_x), speed * dt);
    policy->gaze_y = approach(policy->gaze_y, policy->gaze_y + alpha * (target_y - policy->gaze_y), speed * dt);
    out.gaze_x = clamp(policy->gaze_x, -1, 1);
    out.gaze_y = clamp(policy->gaze_y, -1, 1);
    if (!head_allowed) {
        /* Re-enabling head permission gives voice detection a complete quiet interval. */
        policy->last_head_ms = now_ms;
    } else if (now_ms - policy->last_head_ms >= FACE_TRACKING_HEAD_INTERVAL_MS) {
        float yaw = out.should_recenter || !out.face_present ? 0 : out.gaze_x * 12;
        /* Match the calibrated downward-only pitch envelope used by the body. */
        float pitch = out.should_recenter || !out.face_present ? 0 : clamp(-out.gaze_y * 6, 0, 6);
        bool moved = fabsf(yaw - policy->last_head_yaw) >= 3 || fabsf(pitch - policy->last_head_pitch) >= 1.5f;
        bool recenter = !out.face_present && (fabsf(policy->last_head_yaw) > 0.01f || fabsf(policy->last_head_pitch) > 0.01f);
        if (moved || recenter) {
            out.head_goal_ready = true;
            out.head_goal.yaw_offset_deg = approach(policy->last_head_yaw, yaw, 4);
            out.head_goal.pitch_offset_deg = approach(policy->last_head_pitch, pitch, 2);
            out.head_goal.recenter = recenter;
            policy->last_head_yaw = out.head_goal.yaw_offset_deg;
            policy->last_head_pitch = out.head_goal.pitch_offset_deg;
            policy->last_head_ms = now_ms;
        }
    }
    /* Allow a new largest face after the old position has been absent long enough. */
    if (out.should_recenter && lost_ms >= 1800) policy->has_target = false;
    return out;
}
