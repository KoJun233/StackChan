#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "body_local_policy.h"
#include "motion_planner.h"

static void verify_template(int start_yaw, int start_pitch,
                             const body_local_raw_frame_t *original, size_t count,
                             unsigned budget, bool expected)
{
    body_local_raw_frame_t frames[3];
    memcpy(frames, original, count * sizeof(frames[0]));
    bool admitted = body_local_prepare_paced_frames(start_yaw, start_pitch,
        frames, count, budget - 100, 8);
    assert(admitted == expected);
    if (!admitted) return;
    unsigned duration = 0;
    for (size_t i = 0; i < count; ++i) {
        duration += frames[i].duration_ms;
        assert(frames[i].duration_ms >= original[i].duration_ms);
        for (int yaw_error = i == 0 ? 0 : -8; yaw_error <= (i == 0 ? 0 : 8); ++yaw_error)
            for (int pitch_error = i == 0 ? 0 : -8; pitch_error <= (i == 0 ? 0 : 8); ++pitch_error) {
                motion_plan_t plan;
                assert(motion_plan_init(&plan, start_yaw + yaw_error, start_pitch + pitch_error,
                    frames[i].yaw, frames[i].pitch, frames[i].duration_ms));
                motion_plan_sample_t sample;
                assert(motion_plan_sample(&plan, frames[i].duration_ms, &sample));
                assert(sample.final && sample.deadline_ms == frames[i].duration_ms);
            }
        start_yaw = frames[i].yaw;
        start_pitch = frames[i].pitch;
    }
    assert(duration <= budget - 100);
}

int main(void)
{
    body_local_goal_t goal;
    assert(!body_local_follow_goal(NAN, 0, false, &goal));
    assert(!body_local_follow_goal(0, INFINITY, true, &goal));
    assert(body_local_follow_goal(100, -100, false, &goal));
    assert(goal.yaw_degrees == 12 && goal.pitch_degrees == 45);
    assert(body_local_follow_goal(-100, 100, false, &goal));
    assert(goal.yaw_degrees == -12 && goal.pitch_degrees == 51);
    assert(body_local_follow_goal(12, 6, true, &goal));
    assert(goal.yaw_degrees == 0 && goal.pitch_degrees == 45);
    puts("PASS local-only yaw/pitch clamp, calibrated down-limit and finite input");
    for (int delta = 0; delta <= 150; ++delta) {
        unsigned duration = body_local_frame_duration(500, 500, 500 + delta, 500);
        motion_plan_t plan;
        if (duration) {
            assert(duration >= 600 && duration <= 1200);
            assert(motion_plan_init(&plan, 500, 500, 500 + delta, 500, duration));
        } else assert(delta * 1875U > MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND * 1200U);
        unsigned recovery = body_local_recovery_duration(500, 500 + delta);
        assert(recovery >= 200 && recovery * MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND >= delta * 1000U);
    }
    assert(body_local_frame_duration(-1, 500, 500, 500) == 0);
    assert(body_local_frame_duration(500, 1001, 500, 500) == 0);
    assert(body_local_goal_is_near(500, 500, 508, 492));
    assert(!body_local_goal_is_near(500, 500, 509, 500));
    puts("PASS local motion/deceleration budget, speed-bounded recovery and near-feedback skip");

    // Existing five fixed templates translated with the production 3.2 raw/deg
    // integer conversion, against each existing safety watchdog budget.
    const body_local_raw_frame_t wake[] = {{475,500,300},{500,557,700},{500,500,500}};
    const body_local_raw_frame_t look[] = {{500,548,650},{500,500,550}};
    const body_local_raw_frame_t nod[] = {{500,564,1000},{500,500,1000}};
    const body_local_raw_frame_t think[] = {{551,516,800},{532,509,450},{500,500,550}};
    const body_local_raw_frame_t drowsy[] = {{462,500,900},{484,509,650},{500,500,600}};
    for (int yaw = -12; yaw <= 12; ++yaw) for (int pitch = 0; pitch <= 6; ++pitch) {
        int start_yaw = 500 + yaw * 32 / 10;
        int start_pitch = 500 + pitch * 32 / 10;
        // WAKE has only 1800 ms, so some valid rightmost follow poses must be
        // rejected before any servo goal rather than exceed that speed/deadline.
        unsigned first = body_local_paced_duration(start_yaw, start_pitch, 475,500,300,6000);
        bool wake_fits = first + 700 + 635 <= 1700;
        verify_template(start_yaw,start_pitch,wake,3,1800,wake_fits);
        verify_template(start_yaw,start_pitch,look,2,1600,true);
        verify_template(start_yaw,start_pitch,nod,2,3000,true);
        verify_template(start_yaw,start_pitch,think,3,2400,true);
        verify_template(start_yaw,start_pitch,drowsy,3,2600,true);
    }
    verify_template(538,519,wake,3,1800,false);
    verify_template(700,700,wake,3,1800,false);
    body_local_raw_frame_t invalid[] = {{1001,500,300}};
    assert(!body_local_prepare_paced_frames(500,500,invalid,1,1700,8));
    assert(!body_local_prepare_paced_frames(500,500,invalid,1,1700,17));
    puts("PASS all five templates from follow poses, valid feedback envelope and pre-goal budget rejection");
}
