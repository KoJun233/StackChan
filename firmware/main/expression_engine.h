#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "interaction_state.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    COMPANION_EMOTION_NEUTRAL = 0,
    COMPANION_EMOTION_HAPPY,
    COMPANION_EMOTION_LOVING,
    COMPANION_EMOTION_SAD,
    COMPANION_EMOTION_ANGRY,
    COMPANION_EMOTION_SURPRISED,
    COMPANION_EMOTION_CONFUSED,
    COMPANION_EMOTION_SHY,
    COMPANION_EMOTION_TIRED,
    COMPANION_EMOTION_FOCUSED,
    COMPANION_EMOTION_NERVOUS,
    COMPANION_EMOTION_CONTENT,
    COMPANION_EMOTION_COUNT,
} companion_emotion_t;

typedef enum {
    COMPANION_EMOTION_INTENSITY_WEAK = 0,
    COMPANION_EMOTION_INTENSITY_MEDIUM,
    COMPANION_EMOTION_INTENSITY_STRONG,
} companion_emotion_intensity_t;

typedef enum {
    COMPANION_BEHAVIOR_NONE = 0,
    COMPANION_BEHAVIOR_BOOT_APPEAR,
    COMPANION_BEHAVIOR_WAKE,
    COMPANION_BEHAVIOR_IDLE_BREATHE,
    COMPANION_BEHAVIOR_PROXIMITY_CURIOUS,
    COMPANION_BEHAVIOR_SHAKE_DIZZY,
    COMPANION_BEHAVIOR_DROWSY_SLEEP,
    COMPANION_BEHAVIOR_ROLE_SWITCH,
} companion_expression_behavior_t;

typedef enum {
    COMPANION_EXPRESSION_PREVIEW_NONE = 0,
    COMPANION_EXPRESSION_PREVIEW_EMOTION,
    COMPANION_EXPRESSION_PREVIEW_SYSTEM,
    COMPANION_EXPRESSION_PREVIEW_BEHAVIOR,
    COMPANION_EXPRESSION_PREVIEW_UPDATING,
} companion_expression_preview_t;

typedef enum {
    COMPANION_EXPRESSION_LAYER_IDLE = 0,
    COMPANION_EXPRESSION_LAYER_EMOTION,
    COMPANION_EXPRESSION_LAYER_INTERACTION,
    COMPANION_EXPRESSION_LAYER_PHYSICAL,
    COMPANION_EXPRESSION_LAYER_SYSTEM,
} companion_expression_layer_t;

typedef enum {
    COMPANION_BODY_CREAM = 0,
    COMPANION_BODY_BLUSH,
    COMPANION_BODY_CORAL,
    COMPANION_BODY_MUTED,
    COMPANION_BODY_UPDATE,
} companion_expression_body_style_t;

typedef struct {
    float scale_x;
    float scale_y;
    float offset_x;
    float offset_y;
    float eye_open;
    /* Independent opening and normalized upper/lower eyelids for each eye. */
    float left_eye_open;
    float right_eye_open;
    float left_upper_lid;
    float right_upper_lid;
    float left_lower_lid;
    float right_lower_lid;
    float left_lower_curve;
    float right_lower_curve;
    float eye_spacing;
    float left_eye_length;
    float right_eye_length;
    float left_eye_thickness;
    float right_eye_thickness;
    float left_eye_angle;
    float right_eye_angle;
    float gaze_x;
    float gaze_y;
    float blush;
    float orbit;
    uint8_t particle_count;
    companion_expression_body_style_t body_style;
    bool sleeping;
} companion_expression_pose_t;

#define COMPANION_EXPRESSION_TURN_ID_SIZE 65U

typedef struct {
    companion_face_state_t system_state;
    companion_emotion_t emotion;
    companion_emotion_intensity_t intensity;
    companion_emotion_t body_emotion;
    uint32_t body_emotion_expires_ms;
    companion_expression_behavior_t behavior;
    companion_expression_layer_t active_layer;
    uint32_t emotion_expires_ms;
    uint32_t behavior_expires_ms;
    uint32_t behavior_started_ms;
    uint32_t transition_started_ms;
    uint32_t transition_duration_ms;
    uint32_t last_tick_ms;
    uint32_t state_key;
    companion_expression_preview_t preview;
    uint8_t preview_value;
    uint32_t preview_expires_ms;
    companion_expression_pose_t transition_from;
    companion_expression_pose_t current;
    char turn_id[COMPANION_EXPRESSION_TURN_ID_SIZE];
    uint32_t turn_playback_started_ms;
    uint32_t turn_emotion_duration_ms;
    uint32_t random_state;
    uint32_t next_blink_ms;
    uint32_t blink_started_ms;
    uint32_t next_gaze_ms;
    uint32_t gaze_started_ms;
    float idle_gaze_from_x;
    float idle_gaze_from_y;
    float idle_gaze_target_x;
    float idle_gaze_target_y;
    bool turn_active;
    bool turn_playback_started;
    bool turn_emotion_received;
    bool turn_emotion_pending;
    bool blinking;
    bool initialized;
    bool updating;
} companion_expression_engine_t;

void companion_expression_engine_init(companion_expression_engine_t *engine, uint32_t now_ms);
void companion_expression_engine_set_system(companion_expression_engine_t *engine,
                                            companion_face_state_t state,
                                            uint32_t now_ms);
void companion_expression_engine_suggest_emotion(companion_expression_engine_t *engine,
                                                  companion_emotion_t emotion,
                                                  companion_emotion_intensity_t intensity,
                                                  uint32_t duration_ms,
                                                  uint32_t now_ms);
/* Canonical turn IDs must come from the authenticated voice lifecycle. All
 * calls reject mismatches; duplicate events never renew an emotion's lifetime.
 * Pending emotion consumes no lifetime before first playback. End without
 * playback discards pending emotion, normal playback end preserves its expiry.
 * Cancel/clear/new turn discard the previous turn and all bound emotion. */
bool companion_expression_engine_turn_begin(companion_expression_engine_t *engine,
                                             const char *turn_id, uint32_t now_ms);
bool companion_expression_engine_turn_emotion(companion_expression_engine_t *engine,
                                               const char *turn_id,
                                               companion_emotion_t emotion,
                                               companion_emotion_intensity_t intensity,
                                               uint32_t duration_ms, uint32_t now_ms);
bool companion_expression_engine_turn_playback_started(companion_expression_engine_t *engine,
                                                        const char *turn_id, uint32_t now_ms);
bool companion_expression_engine_turn_end(companion_expression_engine_t *engine,
                                           const char *turn_id, uint32_t now_ms);
bool companion_expression_engine_turn_cancel(companion_expression_engine_t *engine,
                                              const char *turn_id, uint32_t now_ms);
void companion_expression_engine_clear_turn(companion_expression_engine_t *engine, uint32_t now_ms);
void companion_expression_engine_trigger(companion_expression_engine_t *engine,
                                         companion_expression_behavior_t behavior,
                                         uint32_t duration_ms,
                                         uint32_t now_ms);
void companion_expression_engine_set_updating(companion_expression_engine_t *engine,
                                              bool updating,
                                              uint32_t now_ms);
void companion_expression_engine_set_body_emotion(companion_expression_engine_t *engine,
                                                  companion_emotion_t emotion, uint32_t now_ms);
void companion_expression_engine_preview(companion_expression_engine_t *engine,
                                         companion_expression_preview_t preview,
                                         uint8_t value,
                                         uint32_t duration_ms,
                                         uint32_t now_ms);
void companion_expression_engine_tick(companion_expression_engine_t *engine,
                                      uint32_t now_ms,
                                      companion_expression_pose_t *pose);
companion_expression_layer_t companion_expression_engine_active_layer(
    const companion_expression_engine_t *engine, uint32_t now_ms);
const char *companion_expression_layer_name(companion_expression_layer_t layer);
bool companion_emotion_parse(const char *value, companion_emotion_t *emotion);
bool companion_emotion_intensity_parse(const char *value,
                                       companion_emotion_intensity_t *intensity);
bool companion_expression_system_parse(const char *value, companion_face_state_t *state,
                                       bool *updating);
bool companion_expression_behavior_parse(const char *value,
                                         companion_expression_behavior_t *behavior);

#ifdef __cplusplus
}
#endif
