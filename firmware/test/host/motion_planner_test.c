#include "motion_planner.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

static void test_trajectory(unsigned int delay_ms)
{
    motion_plan_t plan;
    assert(motion_plan_init(&plan, 500, 600, 551, 536, 1000));
    unsigned int elapsed = 0;
    unsigned int samples = 0;
    int yaw = 500, pitch = 600;
    for (;;) {
        motion_plan_sample_t next;
        assert(motion_plan_sample(&plan, elapsed, &next));
        assert(next.yaw >= yaw && next.yaw <= 551);
        assert(next.pitch <= pitch && next.pitch >= 536);
        assert(next.deadline_ms <= 1000);
        assert(next.final || next.deadline_ms > elapsed);
        unsigned int goal_ms = motion_plan_goal_time(&next, yaw, pitch);
        assert((unsigned int)abs(next.yaw - yaw) * 1000U <=
               MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND * goal_ms);
        assert((unsigned int)abs(next.pitch - pitch) * 1000U <=
               MOTION_PLAN_MAX_SPEED_RAW_PER_SECOND * goal_ms);
        yaw = next.yaw;
        pitch = next.pitch;
        samples++;
        if (next.final) break;
        // Simulate UART/sensor/scheduler latency after the absolute wait.
        elapsed = next.deadline_ms + delay_ms;
    }
    assert(yaw == 551 && pitch == 536);
    assert(samples <= 40);
    if (delay_ms == 0) assert(samples == 40);
}

int main(void)
{
    motion_plan_t plan;
    assert(!motion_plan_init(NULL, 500, 500, 500, 500, 1000));
    assert(!motion_plan_init(&plan, -1, 500, 500, 500, 1000));
    assert(!motion_plan_init(&plan, 500, 1001, 500, 500, 1000));
    assert(!motion_plan_init(&plan, 500, 500, 1001, 500, 1000));
    assert(!motion_plan_init(&plan, 500, 500, 500, 500, 0));
    assert(!motion_plan_init(&plan, 500, 500, 900, 500, 300));
    assert(motion_plan_init(&plan, 500, 500, 526, 500, 300));
    motion_plan_sample_t sample;
    assert(motion_plan_sample(&plan, 0, &sample));
    assert(sample.yaw == 500); // easing starts without an abrupt velocity jump
    assert(motion_plan_sample(&plan, UINT32_MAX, &sample));
    assert(sample.final && sample.yaw == 526 && sample.deadline_ms == 300);
    for (unsigned int delay = 0; delay <= 200; delay += 5) test_trajectory(delay);
    puts("PASS motion: monotonic endpoints, bounded speed, eased start, absolute deadlines and 41 latency scenarios");
    return 0;
}
