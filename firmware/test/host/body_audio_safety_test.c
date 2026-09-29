#include <assert.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>

#include "safety_state.h"

static atomic_uint stop_count;
static const safety_motion_guard_t idle_guard = {.connected = true};

static void stopped(void *context)
{
    (void)context;
    atomic_fetch_add(&stop_count, 1);
}

static void arm(void)
{
    safety_state_init();
    safety_state_set_motion_capabilities(true, true);
    safety_state_set_calibrated(true);
    assert(safety_state_set_admin_enabled(true));
    atomic_store(&stop_count, 0);
    safety_state_register_stop_callback(stopped, NULL);
}

static safety_diagnostics_t diagnostics(void)
{
    safety_diagnostics_t value = {0};
    safety_state_get_diagnostics(&value);
    return value;
}

static void idle_audio(void)
{
    arm();
    safety_state_begin_audio();
    assert(safety_state_current() == SAFETY_STATE_MOTION_ARMED);
    assert(diagnostics().failure_count == 0);
    assert(atomic_load(&stop_count) == 0);
    /* Even a guard sampled before audio started cannot enter movement. */
    assert(!safety_state_begin_motion(SAFETY_MOTION_NOD_SMALL, &idle_guard, 1));
    assert(diagnostics().last_failure == SAFETY_FAILURE_AUDIO_BUSY);
    safety_state_end_audio();
    assert(safety_state_begin_motion(SAFETY_MOTION_NOD_SMALL, &idle_guard, 2));
    safety_state_complete_motion();
    assert(safety_state_current() == SAFETY_STATE_MOTION_ARMED);
    puts("PASS idle audio preserves permission and rejects movement during audio");
}

static void overlapping_audio(void)
{
    arm();
    safety_state_begin_audio();
    safety_state_begin_audio();
    safety_state_end_audio();
    assert(!safety_state_begin_motion(SAFETY_MOTION_WAKE, &idle_guard, 1));
    assert(!safety_state_set_admin_enabled(true));
    safety_state_end_audio();
    assert(safety_state_begin_motion(SAFETY_MOTION_WAKE, &idle_guard, 2));
    safety_state_complete_motion();
    puts("PASS overlapping voice and queued reminders remain blocked until both end");
}

static void interrupted_motion(void)
{
    arm();
    assert(safety_state_begin_motion(SAFETY_MOTION_THINK, &idle_guard, 1));
    safety_state_begin_audio();
    safety_diagnostics_t value = diagnostics();
    assert(value.state == SAFETY_STATE_MOTION_DISABLED);
    assert(value.motion_runtime == SAFETY_MOTION_IDLE);
    assert(value.last_failure == SAFETY_FAILURE_VOICE_STOP);
    assert(value.failure_count == 1);
    assert(atomic_load(&stop_count) == 1);
    safety_state_end_audio();
    assert(safety_state_current() == SAFETY_STATE_MOTION_DISABLED);
    assert(!safety_state_begin_motion(SAFETY_MOTION_THINK, &idle_guard, 2));
    puts("PASS audio interruption stops hardware once and never rearms or replays");
}

static void disabled_stays_disabled(void)
{
    const safety_failure_code_t reasons[] = {
        SAFETY_FAILURE_TOUCH_STOP, SAFETY_FAILURE_FEEDBACK_FAULT,
        SAFETY_FAILURE_TIMEOUT, SAFETY_FAILURE_DEVICE_ERROR,
    };
    safety_state_init();
    safety_state_begin_audio();
    safety_state_end_audio();
    assert(safety_state_current() == SAFETY_STATE_MOTION_DISABLED);
    for (unsigned index = 0; index < sizeof(reasons) / sizeof(reasons[0]); index++) {
        arm();
        safety_state_begin_audio();
        safety_state_fail_motion(reasons[index]);
        safety_state_end_audio();
        assert(safety_state_current() == SAFETY_STATE_MOTION_DISABLED);
        assert(diagnostics().last_failure == reasons[index]);
    }
    arm();
    safety_state_begin_audio();
    safety_state_stop_motion(); /* Disconnect or explicit stop. */
    safety_state_end_audio();
    assert(safety_state_current() == SAFETY_STATE_MOTION_DISABLED);
    arm();
    safety_state_begin_audio();
    safety_state_set_admin_enabled(false);
    safety_state_end_audio();
    assert(safety_state_current() == SAFETY_STATE_MOTION_DISABLED);
    puts("PASS boot, disconnect, admin stop and faults stay disabled after audio");
}

static void guards_still_apply(void)
{
    arm();
    safety_state_begin_audio();
    safety_state_end_audio();
    safety_motion_guard_t guard = idle_guard;
    guard.audio_busy = true;
    assert(!safety_state_begin_motion(SAFETY_MOTION_WAKE, &guard, 1));
    guard.audio_busy = false;
    guard.connected = false;
    assert(!safety_state_begin_motion(SAFETY_MOTION_WAKE, &guard, 2));
    guard.connected = true;
    guard.updating = true;
    assert(!safety_state_begin_motion(SAFETY_MOTION_WAKE, &guard, 3));
    guard.updating = false;
    guard.device_error = true;
    assert(!safety_state_begin_motion(SAFETY_MOTION_WAKE, &guard, 4));
    safety_state_begin_audio();
    safety_state_set_admin_enabled(false);
    assert(!safety_state_set_admin_enabled(true));
    safety_state_end_audio();
    assert(safety_state_current() == SAFETY_STATE_MOTION_DISABLED);
    puts("PASS all motion guards and audio-time arm rejection remain enforced");
}

static atomic_bool start_race;
static bool motion_accepted;

static void *audio_thread(void *context)
{
    (void)context;
    while (!atomic_load(&start_race)) {}
    safety_state_begin_audio();
    return NULL;
}

static void *motion_thread(void *context)
{
    (void)context;
    while (!atomic_load(&start_race)) {}
    motion_accepted = safety_state_begin_motion(SAFETY_MOTION_WAKE, &idle_guard, 1);
    return NULL;
}

static void concurrent_start(void)
{
    for (unsigned index = 0; index < 500; index++) {
        arm();
        atomic_store(&start_race, false);
        motion_accepted = false;
        pthread_t audio, motion;
        assert(pthread_create(&audio, NULL, audio_thread, NULL) == 0);
        assert(pthread_create(&motion, NULL, motion_thread, NULL) == 0);
        atomic_store(&start_race, true);
        assert(pthread_join(audio, NULL) == 0);
        assert(pthread_join(motion, NULL) == 0);
        assert(diagnostics().motion_runtime == SAFETY_MOTION_IDLE);
        if (motion_accepted) {
            assert(safety_state_current() == SAFETY_STATE_MOTION_DISABLED);
            assert(atomic_load(&stop_count) == 1);
        } else {
            assert(safety_state_current() == SAFETY_STATE_MOTION_ARMED);
            assert(atomic_load(&stop_count) == 0);
        }
        safety_state_end_audio();
    }
    puts("PASS 500 concurrent audio/motion starts never leave movement running");
}

int main(void)
{
    idle_audio();
    overlapping_audio();
    interrupted_motion();
    disabled_stays_disabled();
    guards_still_apply();
    concurrent_start();
    return 0;
}
