#include <assert.h>
#include <stdio.h>

#include "continuous_conversation.h"
#include "touch_interaction.h"
#include "display_frame_policy.h"
#include "companion_hardware.h"
#include "voice_capture_policy.h"
#include "body_touch_policy.h"
#include "expression_engine.h"
#include "voice_protocol.h"
#include "audio_wav.h"
#include <string.h>

static void touch_controls(void)
{
    assert(touch_interaction_face_gesture(TOUCH_INTERACTION_IDLE, 200, 180, 100, 100, 105) == TOUCH_FACE_GESTURE_PTT);
    assert(touch_interaction_face_gesture(TOUCH_INTERACTION_IDLE, 200, 100, 100, 180, 105) == TOUCH_FACE_GESTURE_WAKE);
    assert(touch_interaction_face_gesture(TOUCH_INTERACTION_IDLE, 200, 120, 180, 124, 80) == TOUCH_FACE_GESTURE_MENU);
    assert(touch_interaction_face_gesture(TOUCH_INTERACTION_IDLE, 600, 180, 100, 100, 105) == TOUCH_FACE_GESTURE_NONE);
    assert(touch_interaction_face_gesture(TOUCH_INTERACTION_IDLE, 49, 180, 100, 100, 105) == TOUCH_FACE_GESTURE_NONE);
    assert(touch_interaction_face_gesture(TOUCH_INTERACTION_IDLE, 200, 180, 100, 140, 60) == TOUCH_FACE_GESTURE_NONE);
    assert(touch_interaction_face_gesture(TOUCH_INTERACTION_PLAYING, 200, 180, 100, 100, 105) == TOUCH_FACE_GESTURE_NONE);
    assert(!touch_interaction_face_moved(180, 100, 192, 112));
    assert(touch_interaction_face_moved(180, 100, 193, 112));
    assert(touch_interaction_capture_press_action(TOUCH_INTERACTION_LISTENING, false) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_capture_press_action(TOUCH_INTERACTION_LISTENING, true) == TOUCH_INTERACTION_ACTION_CANCEL);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 100, false, 80, 100, 80, 100) == TOUCH_INTERACTION_ACTION_SUBMIT);
    /* The former button corner follows the same policy as every other point. */
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 100, false, 280, 200, 280, 200) == TOUCH_INTERACTION_ACTION_SUBMIT);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 29, false, 80, 100, 80, 100) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 600, false, 80, 100, 80, 100) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 200, true, 80, 100, 100, 140) == TOUCH_INTERACTION_ACTION_CANCEL);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 200, true, 80, 100, 101, 140) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 200, true, 80, 100, 80, 139) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_LISTENING, 100, true, 80, 100, 80, 100) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_PROCESSING, 100, false, 80, 100, 80, 100) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_wake_record_release_action(TOUCH_INTERACTION_PLAYING, 200, true, 80, 100, 80, 160) == TOUCH_INTERACTION_ACTION_NONE);
    assert(touch_interaction_press_action(TOUCH_INTERACTION_PLAYING) == TOUCH_INTERACTION_ACTION_CANCEL);
    assert(touch_interaction_release_action(TOUCH_INTERACTION_FEEDBACK, 100) == TOUCH_INTERACTION_ACTION_DISMISS);
    assert(touch_interaction_should_start_press_to_talk(TOUCH_INTERACTION_IDLE, 600, true, false));
    assert(!touch_interaction_should_start_press_to_talk(TOUCH_INTERACTION_IDLE, 600, false, false));
    puts("PASS submit, drag cancellation, playback cancellation, PTT and offline gates");
}

static void capture_budgets(void)
{
    voice_capture_policy_t policy = {0};
    for (int i = 0; i < 28; ++i) {
        assert(voice_capture_policy_window(&policy, 50, 350, 200, false) == VOICE_CAPTURE_WAIT);
    }
    assert(policy.speech_windows == 0);
    assert(!voice_capture_policy_wait_expired(&policy, 2800, 3000));
    assert(voice_capture_policy_window(&policy, 500, 350, 200, false) == VOICE_CAPTURE_WAIT);
    assert(voice_capture_policy_window(&policy, 500, 350, 200, false) == VOICE_CAPTURE_START);
    assert(!voice_capture_policy_wait_expired(&policy, 3100, 3000));
    for (int i = 0; i < 40; ++i) {
        assert(voice_capture_policy_window(&policy, 500, 350, 200, false) == VOICE_CAPTURE_KEEP);
    }
    for (int i = 0; i < 7; ++i) {
        assert(voice_capture_policy_window(&policy, 50, 350, 200, false) == VOICE_CAPTURE_KEEP);
    }
    assert(voice_capture_policy_window(&policy, 50, 350, 200, false) == VOICE_CAPTURE_END);
    voice_capture_policy_t empty = {0};
    assert(voice_capture_policy_wait_expired(&empty, 3000, 3000));
    assert(voice_capture_policy_window(&empty, 500, 350, 200, false) == VOICE_CAPTURE_WAIT);
    assert(voice_capture_policy_window(&empty, 50, 350, 200, false) == VOICE_CAPTURE_WAIT);
    assert(!empty.speech_started);
    puts("PASS late speech keeps its budget; bounded silence and isolated noise do not start upload");
}

static void ptt_and_follow_up(void)
{
    voice_capture_policy_t policy = {0};
    assert(voice_capture_policy_window(&policy, 0, 350, 200, true) == VOICE_CAPTURE_WAIT);
    assert(voice_capture_policy_window(&policy, 100, 350, 200, true) == VOICE_CAPTURE_START);
    assert(voice_capture_policy_window(&policy, 500, 350, 200, true) == VOICE_CAPTURE_KEEP);
    for (int i = 0; i < 60; ++i) {
        assert(voice_capture_policy_window(&policy, 50, 350, 200, true) == VOICE_CAPTURE_KEEP);
    }
    continuous_conversation_settings_t settings = {.enabled = true, .follow_up_window_seconds = 3};
    assert(!continuous_conversation_should_offer_follow_up_for_input(&settings, 0, 1000, false, true, true));
    assert(continuous_conversation_should_offer_follow_up_for_input(&settings, 0, 1000, false, true, false));
    assert(!continuous_conversation_should_offer_follow_up_for_input(&settings, 0, 120000, false, true, false));
    assert(!continuous_conversation_should_offer_follow_up_for_input(&settings, 3, 1000, false, true, false));
    assert(!continuous_conversation_should_offer_follow_up_for_input(&settings, 0, 1000, true, true, false));
    assert(!continuous_conversation_should_offer_follow_up_for_input(&settings, 0, 1000, false, false, false));
    assert(continuous_conversation_capture_seconds(&settings, 119000) == 1);
    puts("PASS PTT ends only on release or hard limit; never opens automatic follow-up");
}

int main(void)
{
    uint32_t skipped=0;
    assert(display_frame_next_deadline(1000000,1012000,60,&skipped)==1016666 && skipped==0);
    assert(display_frame_next_deadline(1000000,1042000,60,&skipped)==1049998 && skipped==2);
    assert(display_frame_next_deadline(1000000,1016666,60,&skipped)==1033332 && skipped==1);
    display_frame_policy_t frame_policy={0}; uint8_t reason=0;
    /* Sustained healthy speech uses the same 60 target, rather than a busy cap. */
    for(unsigned now=1000;now<14000;now+=16)
        assert(display_frame_adapt(&frame_policy,now,60,30,60,false,true,6000,200,0,&reason)==60);
    for(unsigned n=0;n<3;n++)assert(display_frame_adapt(&frame_policy,15000+n,60,30,60,false,true,20000,200,0,&reason)==60);
    assert(display_frame_adapt(&frame_policy,15004,60,30,60,false,true,20000,200,0,&reason)==55);
    assert(reason==COMPANION_EXPRESSION_DEGRADE_DRAW_BUDGET);
    assert(display_frame_adapt(&frame_policy,25003,55,30,60,false,true,6000,200,0,&reason)==55);
    assert(display_frame_adapt(&frame_policy,25004,55,30,60,false,true,6000,200,0,&reason)==60);
    assert(display_frame_adapt(&frame_policy,26000,60,30,60,false,true,6000,200,1,&reason)==30);
    assert(reason==COMPANION_EXPRESSION_DEGRADE_AUDIO_UNDERRUN);
    assert(display_frame_adapt(&frame_policy,35000,30,30,60,true,true,6000,200,1,&reason)==30);
    assert(display_frame_adapt(&frame_policy,36000,30,30,60,false,true,6000,200,1,&reason)==35);
    frame_policy=(display_frame_policy_t){0};reason=0;
    assert(display_frame_adapt(&frame_policy,40000,60,60,60,true,true,6000,200,1,&reason)==30);
    reason=COMPANION_EXPRESSION_DEGRADE_NONE; /* Mode/screen transitions cannot erase the audio guard. */
    assert(display_frame_adapt(&frame_policy,49999,60,60,60,true,true,6000,200,1,&reason)==30);
    assert(display_frame_adapt(&frame_policy,50000,30,60,60,true,true,6000,200,1,&reason)==60);
    puts("PASS absolute frame cadence skips late slots; healthy audio stays at 60; budget/audio recovery is bounded");
    uint8_t frame[50] = {0};
    int16_t sample = 100;
    size_t size = 0;
    assert(audio_wav_build_pcm16_mono(frame + 4, 46, &sample, 1, 16000, &size) == ESP_OK);
    uint32_t sequence = 0;
    const uint8_t *wav = NULL;
    size_t wav_size = 0;
    assert(voice_protocol_accept_stream_audio(frame, sizeof(frame), &sequence, &wav, &wav_size));
    assert(sequence == 1);
    assert(!voice_protocol_accept_stream_audio(frame, sizeof(frame), &sequence, &wav, &wav_size));
    assert(sequence == 1);
    const char complete[] = "{\"segmentCount\":1}";
    assert(voice_protocol_parse_stream_complete((const uint8_t *)complete, strlen(complete), sequence));
    sequence = VOICE_PROTOCOL_STREAM_MAX_SEGMENTS;
    assert(!voice_protocol_accept_stream_audio(frame, sizeof(frame), &sequence, &wav, &wav_size));
    puts("PASS consumed audio advances sequence even when explicit-end skips playback; duplicate/limit rejected");
    touch_controls();
    capture_budgets();
    ptt_and_follow_up();
    assert(body_touch_release_action(200, false, true, true) == BODY_TOUCH_AFFECTION);
    assert(body_touch_release_action(200, true, true, true) == BODY_TOUCH_NONE);
    assert(body_touch_release_action(2000, true, true, true) == BODY_TOUCH_NONE);
    assert(body_touch_release_action(200, false, false, true) == BODY_TOUCH_NONE);
    assert(body_touch_release_action(200, false, true, false) == BODY_TOUCH_NONE);
    assert(body_touch_release_action(2000, false, false, false) == BODY_TOUCH_WORKDAY_TOGGLE);
    companion_expression_engine_t engine = {0};
    companion_expression_engine_init(&engine, 0);
    companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_NONE, 0, 2000);
    companion_expression_engine_suggest_emotion(&engine, COMPANION_EMOTION_LOVING,
                                               COMPANION_EMOTION_INTENSITY_WEAK, 5000, 2000);
    companion_expression_engine_set_body_emotion(&engine, COMPANION_EMOTION_FOCUSED, 2000);
    assert(companion_expression_engine_active_layer(&engine, 2000) == COMPANION_EXPRESSION_LAYER_EMOTION);
    companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_SHAKE_DIZZY, 2000, 2000);
    companion_expression_engine_set_system(&engine, COMPANION_FACE_LISTENING, 2000);
    assert(companion_expression_engine_active_layer(&engine, 2000) == COMPANION_EXPRESSION_LAYER_INTERACTION);
    companion_expression_engine_t without_shake = engine;
    without_shake.behavior = COMPANION_BEHAVIOR_NONE;
    companion_expression_pose_t pose;
    companion_expression_engine_tick(&engine, 2400, &pose);
    companion_expression_engine_tick(&without_shake, 2400, &pose);
    assert(engine.current.offset_x == without_shake.current.offset_x);
    assert(engine.current.left_eye_angle == without_shake.current.left_eye_angle);
    companion_expression_engine_set_system(&engine, COMPANION_FACE_PROCESSING, 2000);
    assert(companion_expression_engine_active_layer(&engine, 2000) == COMPANION_EXPRESSION_LAYER_INTERACTION);
    companion_expression_engine_set_system(&engine, COMPANION_FACE_SPEAKING, 2000);
    assert(companion_expression_engine_active_layer(&engine, 2000) == COMPANION_EXPRESSION_LAYER_INTERACTION);
    companion_expression_engine_set_updating(&engine, true, 2000);
    assert(companion_expression_engine_active_layer(&engine, 2000) == COMPANION_EXPRESSION_LAYER_SYSTEM);
    companion_expression_engine_set_body_emotion(&engine, COMPANION_EMOTION_NEUTRAL, 2000);
    assert(engine.emotion == COMPANION_EMOTION_LOVING);
    assert(engine.body_emotion == COMPANION_EMOTION_NEUTRAL);
    puts("PASS affection, long press and stop precedence; motion expressions preserve voice and role emotion");
    return 0;
}
