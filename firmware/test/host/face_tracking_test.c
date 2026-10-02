#include <assert.h>
#include <math.h>
#include <stdio.h>

#include "face_tracking_policy.h"

static face_tracking_candidate_t box(float x, float y, float size)
{
    return (face_tracking_candidate_t){x - size / 2, y - size / 2, x + size / 2, y + size / 2, 0.95f};
}

static void test_largest_then_continuity(void)
{
    face_tracking_policy_t policy;
    face_tracking_policy_init(&policy);
    face_tracking_candidate_t faces[] = {box(0.2f, 0.5f, 0.15f), box(0.7f, 0.5f, 0.3f)};
    face_tracking_policy_update(&policy, true, false, faces, 2, 1000);
    assert(fabsf(policy.target_x - 0.4f) < 0.001f);
    faces[0] = box(0.25f, 0.5f, 0.45f); /* Bigger passerby does not steal current target. */
    faces[1] = box(0.72f, 0.5f, 0.15f);
    face_tracking_policy_output_t out = face_tracking_policy_update(&policy, true, false, faces, 2, 1333);
    assert(fabsf(policy.target_x - 0.44f) < 0.001f);
    assert(out.face_present && out.gaze_x > 0);
    out = face_tracking_policy_update(&policy, true, false, faces, 1, 1666);
    assert(fabsf(policy.target_x - 0.44f) < 0.001f && out.face_present);
    out = face_tracking_policy_update(&policy, true, false, faces, 1, 3000);
    assert(out.should_recenter && !out.face_present);
    face_tracking_policy_update(&policy, true, false, faces, 1, 3333);
    face_tracking_policy_update(&policy, true, false, faces, 1, 3666);
    assert(policy.target_x < 0); /* New largest only after the old target's loss grace. */
    puts("PASS largest initial face and position continuity through a larger passerby");
}

static void test_loss_and_velocity(void)
{
    face_tracking_policy_t policy;
    face_tracking_policy_init(&policy);
    face_tracking_candidate_t face = box(0.8f, 0.3f, 0.2f);
    face_tracking_policy_output_t out = face_tracking_policy_update(&policy, true, false, &face, 1, 1000);
    float previous = out.gaze_x;
    for (uint64_t now = 1333; now <= 2665; now += 333) {
        out = face_tracking_policy_update(&policy, true, false, &face, 1, now);
        assert(fabsf(out.gaze_x - previous) <= 1.8f * 0.333f + 0.001f);
        previous = out.gaze_x;
    }
    out = face_tracking_policy_update(&policy, true, false, NULL, 0, 2998);
    assert(out.face_present && !out.should_recenter);
    out = face_tracking_policy_update(&policy, true, false, NULL, 0, 4000);
    assert(!out.face_present && out.should_recenter);
    assert(out.gaze_x >= previous - 0.4f - 0.001f);
    for (uint64_t now = 4333; now < 10000; now += 333) {
        float before = out.gaze_x;
        out = face_tracking_policy_update(&policy, true, false, NULL, 0, now);
        assert(fabsf(out.gaze_x - before) <= 0.8f * 0.333f + 0.001f);
    }
    assert(fabsf(out.gaze_x) < 0.01f && fabsf(out.gaze_y) < 0.01f);
    puts("PASS short loss hold, sustained loss recenter and bounded gaze velocity");
}

static void test_head_budget_and_gates(void)
{
    face_tracking_policy_t policy;
    face_tracking_policy_init(&policy);
    face_tracking_candidate_t face = box(0.9f, 0.2f, 0.2f);
    unsigned goals = 0;
    uint64_t previous_goal = 0;
    float previous_yaw = 0, previous_pitch = 0;
    for (uint64_t now = 1000; now < 26000; now += 250) {
        face_tracking_policy_output_t out = face_tracking_policy_update(&policy, true, true, &face, 1, now);
        if (!out.head_goal_ready) continue;
        assert(now - previous_goal >= FACE_TRACKING_HEAD_INTERVAL_MS);
        assert(fabsf(out.head_goal.yaw_offset_deg) <= 12);
        assert(out.head_goal.pitch_offset_deg >= 0 && out.head_goal.pitch_offset_deg <= 6);
        assert(fabsf(out.head_goal.yaw_offset_deg - previous_yaw) <= 4);
        assert(fabsf(out.head_goal.pitch_offset_deg - previous_pitch) <= 2);
        previous_goal = now;
        previous_yaw = out.head_goal.yaw_offset_deg;
        previous_pitch = out.head_goal.pitch_offset_deg;
        goals++;
    }
    assert(goals > 0 && goals <= 4);
    face_tracking_policy_t lower_face_policy;
    face_tracking_policy_init(&lower_face_policy);
    face_tracking_candidate_t lower_face = box(0.5f, 0.8f, 0.2f);
    for (uint64_t now = 1000; now < 26000; now += 250) {
        face_tracking_policy_output_t lower = face_tracking_policy_update(
            &lower_face_policy, true, true, &lower_face, 1, now);
        assert(lower.gaze_y >= 0);
        assert(lower_face_policy.last_head_pitch == 0);
        assert(!lower.head_goal_ready); /* Downward pitch would exceed current calibration. */
    }
    face_tracking_policy_output_t out = face_tracking_policy_update(&policy, true, false, &face, 1, 30000);
    assert(!out.head_goal_ready);
    out = face_tracking_policy_update(&policy, true, true, &face, 1, 33000);
    assert(!out.head_goal_ready); /* Reopened head permission still preserves the quiet interval. */
    out = face_tracking_policy_update(&policy, false, true, &face, 1, 34000);
    assert(!out.face_present && !out.head_goal_ready && out.gaze_x == 0 && !policy.active);
    out = face_tracking_policy_update(&policy, true, true, &face, 1, 35000);
    assert(!out.head_goal_ready && out.gaze_x == 0);
    for (uint64_t now = 35250; now <= 42000; now += 250)
        out = face_tracking_policy_update(&policy, true, true, &face, 1, now);
    assert(policy.last_head_yaw != 0);
    for (uint64_t now = 42250; now <= 48000; now += 250) {
        out = face_tracking_policy_update(&policy, true, true, NULL, 0, now);
        if (out.head_goal_ready) assert(out.head_goal.recenter);
    }
    assert(policy.last_head_yaw == 0 && policy.last_head_pitch == 0);
    puts("PASS local head limits, 6-second quiet interval, permission reset and loss return");
}

static void test_invalid_and_deadzone(void)
{
    face_tracking_policy_t policy;
    face_tracking_policy_init(&policy);
    face_tracking_candidate_t faces[] = {
        {NAN, 0, 1, 1, 1}, {0, 0, 1, 1, NAN}, {-1, 0, 1, 1, 1},
        {0, 0, 1, 1, 0.5f}, {0.4f, 0.4f, 0.41f, 0.41f, 1}, box(0.53f, 0.48f, 0.2f),
    };
    for (uint64_t now = 1000; now <= 9000; now += 250) {
        face_tracking_policy_output_t out = face_tracking_policy_update(&policy, true, true, faces, 6, now);
        assert(out.face_present && out.gaze_x == 0 && out.gaze_y == 0 && !out.head_goal_ready);
    }
    face_tracking_policy_output_t out = face_tracking_policy_update(&policy, true, true, faces, 6, 1);
    assert(out.gaze_x == 0 && !out.head_goal_ready); /* Clock reset must not underflow timers. */
    puts("PASS malformed/low-score boxes, central dead zone and monotonic-time reset");
}

int main(void)
{
    test_largest_then_continuity();
    test_loss_and_velocity();
    test_head_budget_and_gates();
    test_invalid_and_deadzone();
    return 0;
}
