#include "expression_engine.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

static companion_expression_engine_t initialized(uint32_t now)
{
    companion_expression_engine_t engine;
    companion_expression_engine_init(&engine, now);
    companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_NONE, 0, now);
    return engine;
}

static void pending_and_playback(void)
{
    companion_expression_engine_t engine = initialized(0);
    companion_expression_pose_t pose;
    assert(!companion_expression_engine_turn_emotion(&engine, "a", COMPANION_EMOTION_HAPPY,
        COMPANION_EMOTION_INTENSITY_STRONG, 5000, 0));
    assert(companion_expression_engine_turn_begin(&engine, "a", 100));
    assert(!companion_expression_engine_turn_begin(&engine, "a", 200));
    companion_expression_engine_set_system(&engine, COMPANION_FACE_PROCESSING, 100);
    assert(companion_expression_engine_turn_emotion(&engine, "a", COMPANION_EMOTION_LOVING,
        COMPANION_EMOTION_INTENSITY_STRONG, 5000, 300));
    companion_expression_engine_tick(&engine, 30000, &pose);
    assert(engine.turn_emotion_pending && engine.emotion == COMPANION_EMOTION_LOVING);
    assert(!companion_expression_engine_turn_playback_started(&engine, "old", 30000));
    assert(companion_expression_engine_turn_playback_started(&engine, "a", 31000));
    assert(engine.emotion_expires_ms == 36000);
    assert(!companion_expression_engine_turn_playback_started(&engine, "a", 32000));
    assert(!companion_expression_engine_turn_emotion(&engine, "a", COMPANION_EMOTION_SAD,
        COMPANION_EMOTION_INTENSITY_STRONG, 15000, 32000));
    companion_expression_engine_set_system(&engine, COMPANION_FACE_SPEAKING, 31000);
    companion_expression_engine_set_body_emotion(&engine, COMPANION_EMOTION_FOCUSED, 31000);
    companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_SHAKE_DIZZY, 2000, 31000);
    companion_expression_engine_tick(&engine, 31000, &pose);
    companion_expression_engine_tick(&engine, 32000, &pose);
    assert(pose.blush > 0.9f && pose.left_lower_lid > 0.6f);
    assert(pose.left_upper_lid < 0.05f); /* Body motion cannot replace speech emotion. */
    assert(companion_expression_engine_turn_end(&engine, "a", 33000));
    assert(engine.emotion == COMPANION_EMOTION_LOVING && engine.emotion_expires_ms == 36000);
    assert(!companion_expression_engine_turn_end(&engine, "a", 34000));
    assert(!companion_expression_engine_turn_emotion(&engine, "a", COMPANION_EMOTION_HAPPY,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, 34000));
    companion_expression_engine_tick(&engine, 36000, &pose);
    assert(engine.emotion == COMPANION_EMOTION_NEUTRAL);
    puts("PASS pending survives slow TTS; first playback anchors once; speaking retains role emotion");
}

static void cancellation_and_ordering(void)
{
    companion_expression_engine_t engine = initialized(0);
    companion_expression_pose_t pose;
    assert(companion_expression_engine_turn_begin(&engine, "first", 100));
    assert(companion_expression_engine_turn_playback_started(&engine, "first", 1000));
    assert(companion_expression_engine_turn_emotion(&engine, "first", COMPANION_EMOTION_SAD,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, 2000));
    assert(engine.emotion_expires_ms == 6000);
    assert(companion_expression_engine_turn_begin(&engine, "second", 3000));
    assert(engine.emotion == COMPANION_EMOTION_NEUTRAL && !engine.turn_emotion_pending);
    assert(!companion_expression_engine_turn_cancel(&engine, "first", 3001));
    assert(!companion_expression_engine_turn_emotion(&engine, "first", COMPANION_EMOTION_HAPPY,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, 3002));
    assert(companion_expression_engine_turn_emotion(&engine, "second", COMPANION_EMOTION_SAD,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, 3003));
    assert(companion_expression_engine_turn_cancel(&engine, "second", 3004));
    assert(!companion_expression_engine_turn_begin(&engine, "second", 3005));
    assert(!companion_expression_engine_turn_playback_started(&engine, "second", 3006));
    assert(companion_expression_engine_turn_begin(&engine, "third", 4000));
    assert(companion_expression_engine_turn_emotion(&engine, "third", COMPANION_EMOTION_SAD,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, 4001));
    assert(companion_expression_engine_turn_end(&engine, "third", 4002));
    assert(engine.emotion == COMPANION_EMOTION_NEUTRAL && !engine.turn_emotion_pending);
    assert(companion_expression_engine_turn_begin(&engine, "late", 5000));
    assert(companion_expression_engine_turn_playback_started(&engine, "late", 5100));
    assert(companion_expression_engine_turn_emotion(&engine, "late", COMPANION_EMOTION_SAD,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, 12000));
    assert(engine.emotion_expires_ms == 10100 && engine.emotion == COMPANION_EMOTION_NEUTRAL);
    companion_expression_engine_tick(&engine, 12001, &pose);
    assert(companion_expression_engine_active_layer(&engine, 12001) == COMPANION_EXPRESSION_LAYER_IDLE);
    puts("PASS old/cancelled/ended turns cannot revive; reordered command uses original anchor");
}

static void system_and_legacy(void)
{
    companion_expression_engine_t engine = initialized(0);
    companion_expression_pose_t pose;
    assert(companion_expression_engine_turn_begin(&engine, "offline", 100));
    assert(companion_expression_engine_turn_emotion(&engine, "offline", COMPANION_EMOTION_HAPPY,
        COMPANION_EMOTION_INTENSITY_STRONG, 5000, 200));
    companion_expression_engine_set_system(&engine, COMPANION_FACE_OFFLINE, 300);
    companion_expression_engine_tick(&engine, 300, &pose);
    assert(!engine.turn_active && !engine.turn_emotion_pending && pose.body_style == COMPANION_BODY_MUTED);
    assert(!companion_expression_engine_turn_playback_started(&engine, "offline", 400));
    companion_expression_engine_set_system(&engine, COMPANION_FACE_IDLE, 400);
    assert(companion_expression_engine_turn_begin(&engine, "update", 500));
    companion_expression_engine_set_updating(&engine, true, 600);
    companion_expression_engine_tick(&engine, 600, &pose);
    assert(!engine.turn_active && pose.body_style == COMPANION_BODY_UPDATE);
    companion_expression_engine_set_updating(&engine, false, 700);
    companion_expression_engine_suggest_emotion(&engine, COMPANION_EMOTION_HAPPY,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, 700);
    assert(!engine.turn_emotion_pending && engine.emotion_expires_ms == 5700);
    companion_expression_engine_tick(&engine, 5700, &pose);
    assert(engine.emotion == COMPANION_EMOTION_NEUTRAL);
    assert(companion_expression_engine_turn_begin(&engine, "disconnect", 6000));
    companion_expression_engine_clear_turn(&engine, 6001);
    assert(!companion_expression_engine_turn_playback_started(&engine, "disconnect", 6002));
    puts("PASS offline/update override immediately; disconnect clears; passive legacy emotion remains bounded");
}

static void validation_and_wrap(void)
{
    companion_expression_engine_t engine = initialized(UINT32_MAX - 3000U);
    companion_expression_pose_t pose;
    char long_id[100];
    memset(long_id, 'x', sizeof(long_id));
    long_id[99] = '\0';
    assert(!companion_expression_engine_turn_begin(&engine, NULL, 0));
    assert(!companion_expression_engine_turn_begin(&engine, "", 0));
    assert(!companion_expression_engine_turn_begin(&engine, long_id, 0));
    assert(companion_expression_engine_turn_begin(&engine, "wrap", UINT32_MAX - 2500U));
    assert(!companion_expression_engine_turn_emotion(&engine, "wrap", COMPANION_EMOTION_HAPPY,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 4999, UINT32_MAX - 2000U));
    assert(!companion_expression_engine_turn_emotion(&engine, "wrap", COMPANION_EMOTION_COUNT,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, UINT32_MAX - 2000U));
    assert(companion_expression_engine_turn_emotion(&engine, "wrap", COMPANION_EMOTION_HAPPY,
        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000, UINT32_MAX - 2000U));
    assert(companion_expression_engine_turn_playback_started(&engine, "wrap", UINT32_MAX - 1000U));
    companion_expression_engine_tick(&engine, 3000, &pose);
    assert(engine.emotion == COMPANION_EMOTION_HAPPY);
    companion_expression_engine_tick(&engine, 4000, &pose);
    assert(engine.emotion == COMPANION_EMOTION_NEUTRAL);
    puts("PASS bounded IDs/fields; lifetime works across uint32 clock wrap");
}

static void natural_motion(void)
{
    companion_expression_engine_t engine = initialized(0);
    companion_expression_pose_t pose;
    unsigned blinks = 0;
    float min_x = 1.0f, max_x = -1.0f;
    for (uint32_t now = 0; now < 60000; now += 30) {
        companion_expression_engine_tick(&engine, now, &pose);
        assert(isfinite(pose.left_eye_open) && pose.left_eye_open >= 0 && pose.left_eye_open <= 1.05f);
        assert(pose.gaze_x >= -0.35f && pose.gaze_x <= 0.35f);
        if (pose.left_eye_open < 0.1f) blinks++;
        if (pose.gaze_x < min_x) min_x = pose.gaze_x;
        if (pose.gaze_x > max_x) max_x = pose.gaze_x;
    }
    assert(blinks >= 8 && max_x - min_x > 0.20f);
    puts("PASS bounded randomized blinks and gaze; no periodic motion accumulation");
}

static void wake_while_listening(void)
{
    companion_expression_engine_t engine = initialized(0);
    companion_expression_pose_t base, awake;
    companion_expression_engine_set_system(&engine, COMPANION_FACE_LISTENING, 100);
    companion_expression_engine_tick(&engine, 100, &base);
    companion_expression_engine_tick(&engine, 1000, &base);
    companion_expression_engine_t control = engine;
    companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_WAKE, 2200, 1000);
    companion_expression_engine_tick(&control, 1240, &base);
    companion_expression_engine_tick(&engine, 1240, &awake);
    assert(engine.system_state == COMPANION_FACE_LISTENING);
    assert(awake.scale_y-base.scale_y > 0.17f && base.offset_y-awake.offset_y > 0.17f);
    companion_expression_engine_tick(&control, 1500, &base);
    companion_expression_engine_tick(&engine, 1500, &awake);
    assert(fabsf(awake.scale_y-base.scale_y) < 0.0001f);
    assert(engine.behavior_expires_ms == 3200); /* Animation never renews TTL. */
    companion_expression_engine_set_system(&engine, COMPANION_FACE_OFFLINE, 1510);
    companion_expression_engine_tick(&engine, 1510, &awake);
    assert(awake.body_style == COMPANION_BODY_MUTED && awake.offset_y > -0.15f);
    engine = initialized(UINT32_MAX-300U);
    companion_expression_engine_set_system(&engine,COMPANION_FACE_LISTENING,UINT32_MAX-250U);
    companion_expression_engine_trigger(&engine,COMPANION_BEHAVIOR_WAKE,900,UINT32_MAX-100U);
    companion_expression_engine_tick(&engine,139U,&awake);
    assert(isfinite(awake.offset_y) && awake.offset_y < -0.16f);
    puts("PASS one visible wake during recording; overlay ends, priority/TTL/wrap retained");
}

int main(void)
{
    pending_and_playback();
    cancellation_and_ordering();
    system_and_legacy();
    validation_and_wrap();
    natural_motion();
    wake_while_listening();
    return 0;
}
