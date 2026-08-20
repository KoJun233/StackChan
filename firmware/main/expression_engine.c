#include "expression_engine.h"

#include <math.h>
#include <stddef.h>
#include <string.h>

#define DEFAULT_EMOTION_MS 10000U
#define MIN_EMOTION_MS 5000U
#define MAX_EMOTION_MS 15000U
#define MIN_BEHAVIOR_MS 1500U
#define MAX_BEHAVIOR_MS 3000U

static float clampf(float value, float minimum, float maximum)
{
    return value < minimum ? minimum : (value > maximum ? maximum : value);
}

static float lerpf(float from, float to, float amount)
{
    return from + (to - from) * amount;
}

static float smoothstep(float amount)
{
    amount = clampf(amount, 0.0f, 1.0f);
    return amount * amount * (3.0f - 2.0f * amount);
}

static companion_expression_pose_t neutral_pose(void)
{
    return (companion_expression_pose_t){
        .scale_x = 1.0f,
        .scale_y = 1.0f,
        .eye_open = 1.0f,
        .eye_spacing = 0.43f,
        .left_eye_length = 0.62f,
        .right_eye_length = 0.62f,
        .left_eye_thickness = 0.29f,
        .right_eye_thickness = 0.29f,
        .left_eye_angle = -0.06f,
        .right_eye_angle = 0.06f,
        .body_style = COMPANION_BODY_CREAM,
    };
}

static float intensity_scale(companion_emotion_intensity_t intensity)
{
    switch (intensity) {
        case COMPANION_EMOTION_INTENSITY_WEAK: return 0.55f;
        case COMPANION_EMOTION_INTENSITY_STRONG: return 1.0f;
        case COMPANION_EMOTION_INTENSITY_MEDIUM:
        default: return 0.78f;
    }
}

static companion_expression_pose_t emotion_pose(companion_emotion_t emotion,
                                                 companion_emotion_intensity_t intensity)
{
    companion_expression_pose_t pose = neutral_pose();
    float amount = intensity_scale(intensity);
    switch (emotion) {
        case COMPANION_EMOTION_HAPPY:
            pose.left_eye_length = 0.66f; pose.right_eye_length = 0.66f;
            pose.left_eye_thickness = 0.30f; pose.right_eye_thickness = 0.30f;
            pose.left_eye_angle = -0.22f; pose.right_eye_angle = 0.22f;
            pose.blush = amount * 0.38f;
            pose.scale_x = 1.0f + 0.04f * amount; pose.scale_y = 1.0f - 0.03f * amount;
            break;
        case COMPANION_EMOTION_LOVING:
            pose.left_eye_length = 0.58f; pose.right_eye_length = 0.58f;
            pose.left_eye_thickness = 0.25f; pose.right_eye_thickness = 0.25f;
            pose.left_eye_angle = 0.28f; pose.right_eye_angle = -0.28f;
            pose.eye_spacing = 0.40f; pose.blush = amount; pose.particle_count = 6;
            pose.body_style = COMPANION_BODY_BLUSH;
            break;
        case COMPANION_EMOTION_SAD:
            pose.left_eye_length = 0.52f; pose.right_eye_length = 0.52f;
            pose.left_eye_thickness = 0.17f; pose.right_eye_thickness = 0.17f;
            pose.left_eye_angle = 0.58f * amount; pose.right_eye_angle = -0.58f * amount;
            pose.gaze_y = 0.16f;
            pose.scale_x = 0.96f; pose.scale_y = 1.04f;
            break;
        case COMPANION_EMOTION_ANGRY:
            pose.left_eye_length = 0.58f; pose.right_eye_length = 0.58f;
            pose.left_eye_thickness = 0.22f; pose.right_eye_thickness = 0.22f;
            pose.left_eye_angle = -0.68f * amount; pose.right_eye_angle = 0.68f * amount;
            pose.eye_spacing = 0.38f;
            pose.scale_x = 1.06f; pose.scale_y = 0.95f;
            pose.body_style = COMPANION_BODY_CORAL;
            break;
        case COMPANION_EMOTION_SURPRISED:
            pose.left_eye_length = 0.43f; pose.right_eye_length = 0.57f;
            pose.left_eye_thickness = 0.38f; pose.right_eye_thickness = 0.46f;
            pose.left_eye_angle = 0.06f; pose.right_eye_angle = -0.08f;
            pose.eye_spacing = 0.40f;
            pose.scale_x = 0.94f; pose.scale_y = 1.09f;
            break;
        case COMPANION_EMOTION_CONFUSED:
            pose.left_eye_length = 0.42f; pose.right_eye_length = 0.65f;
            pose.left_eye_thickness = 0.19f; pose.right_eye_thickness = 0.29f;
            pose.left_eye_angle = 0.72f; pose.right_eye_angle = -0.18f;
            pose.eye_spacing = 0.41f; pose.gaze_x = 0.12f;
            pose.scale_x = 0.98f; pose.scale_y = 1.02f;
            break;
        case COMPANION_EMOTION_SHY:
            pose.left_eye_length = 0.45f; pose.right_eye_length = 0.45f;
            pose.left_eye_thickness = 0.17f; pose.right_eye_thickness = 0.17f;
            pose.left_eye_angle = 0.78f; pose.right_eye_angle = -0.78f;
            pose.gaze_x = -0.10f; pose.gaze_y = 0.12f; pose.blush = amount;
            pose.body_style = COMPANION_BODY_BLUSH;
            break;
        case COMPANION_EMOTION_TIRED:
            pose.left_eye_length = 0.45f; pose.right_eye_length = 0.45f;
            pose.left_eye_thickness = 0.10f; pose.right_eye_thickness = 0.10f;
            pose.left_eye_angle = 1.28f; pose.right_eye_angle = -1.28f;
            pose.eye_open = 0.72f; pose.gaze_y = 0.14f; pose.scale_y = 0.95f;
            break;
        case COMPANION_EMOTION_FOCUSED:
            pose.left_eye_length = 0.52f; pose.right_eye_length = 0.52f;
            pose.left_eye_thickness = 0.16f; pose.right_eye_thickness = 0.16f;
            pose.left_eye_angle = -0.52f; pose.right_eye_angle = 0.52f;
            pose.eye_spacing = 0.37f;
            break;
        case COMPANION_EMOTION_NERVOUS:
            pose.left_eye_length = 0.39f; pose.right_eye_length = 0.39f;
            pose.left_eye_thickness = 0.36f; pose.right_eye_thickness = 0.36f;
            pose.left_eye_angle = 0.10f; pose.right_eye_angle = -0.10f;
            pose.eye_spacing = 0.40f; pose.gaze_x = 0.08f;
            pose.scale_x = 0.98f; pose.scale_y = 1.03f;
            break;
        case COMPANION_EMOTION_CONTENT:
            pose.left_eye_length = 0.54f; pose.right_eye_length = 0.54f;
            pose.left_eye_thickness = 0.23f; pose.right_eye_thickness = 0.23f;
            pose.left_eye_angle = -0.10f; pose.right_eye_angle = 0.10f;
            pose.blush = amount * 0.30f;
            pose.scale_x = 1.01f; pose.scale_y = 0.99f;
            break;
        case COMPANION_EMOTION_NEUTRAL:
        default:
            break;
    }
    return pose;
}

static companion_expression_pose_t system_pose(companion_face_state_t state)
{
    companion_expression_pose_t pose = neutral_pose();
    switch (state) {
        case COMPANION_FACE_LISTENING:
            pose.left_eye_length = 0.49f; pose.right_eye_length = 0.49f;
            pose.left_eye_thickness = 0.39f; pose.right_eye_thickness = 0.39f;
            pose.eye_spacing = 0.40f; pose.orbit = 0.38f;
            break;
        case COMPANION_FACE_PROCESSING:
            pose.left_eye_length = 0.54f; pose.right_eye_length = 0.42f;
            pose.left_eye_thickness = 0.19f; pose.right_eye_thickness = 0.17f;
            pose.left_eye_angle = -0.42f; pose.right_eye_angle = 0.30f;
            pose.eye_spacing = 0.38f; pose.orbit = 1.0f;
            break;
        case COMPANION_FACE_SPEAKING:
            pose.left_eye_length = 0.62f; pose.right_eye_length = 0.62f;
            pose.left_eye_thickness = 0.27f; pose.right_eye_thickness = 0.27f;
            pose.scale_x = 1.04f; pose.scale_y = 0.97f;
            break;
        case COMPANION_FACE_SUCCESS:
            pose = emotion_pose(COMPANION_EMOTION_CONTENT, COMPANION_EMOTION_INTENSITY_STRONG);
            pose.particle_count = 6;
            break;
        case COMPANION_FACE_NO_SPEECH:
            pose = emotion_pose(COMPANION_EMOTION_CONFUSED, COMPANION_EMOTION_INTENSITY_MEDIUM);
            break;
        case COMPANION_FACE_OFFLINE:
            pose.left_eye_length = 0.44f; pose.right_eye_length = 0.44f;
            pose.left_eye_thickness = 0.09f; pose.right_eye_thickness = 0.09f;
            pose.left_eye_angle = 1.42f; pose.right_eye_angle = -1.42f;
            pose.eye_open = 0.68f; pose.gaze_y = 0.16f;
            pose.body_style = COMPANION_BODY_MUTED;
            break;
        case COMPANION_FACE_RECOVERABLE_ERROR:
            pose.left_eye_length = 0.58f; pose.right_eye_length = 0.58f;
            pose.left_eye_thickness = 0.22f; pose.right_eye_thickness = 0.22f;
            pose.left_eye_angle = -0.72f; pose.right_eye_angle = 0.72f;
            pose.eye_spacing = 0.38f; pose.scale_x = 1.08f;
            pose.scale_y = 0.93f;
            pose.body_style = COMPANION_BODY_CORAL;
            break;
        case COMPANION_FACE_IDLE:
        default:
            break;
    }
    return pose;
}

static bool system_is_high_priority(companion_face_state_t state)
{
    return state == COMPANION_FACE_OFFLINE || state == COMPANION_FACE_RECOVERABLE_ERROR;
}

static bool system_is_interaction(companion_face_state_t state)
{
    return state != COMPANION_FACE_IDLE && !system_is_high_priority(state);
}

static companion_expression_pose_t behavior_pose(companion_expression_behavior_t behavior)
{
    companion_expression_pose_t pose = neutral_pose();
    switch (behavior) {
        case COMPANION_BEHAVIOR_BOOT_APPEAR:
            pose.scale_x = 0.82f; pose.scale_y = 0.82f; pose.eye_open = 0.18f;
            break;
        case COMPANION_BEHAVIOR_WAKE:
            pose.scale_x = 0.94f; pose.scale_y = 1.08f; pose.eye_open = 1.15f;
            break;
        case COMPANION_BEHAVIOR_IDLE_BREATHE:
            break;
        case COMPANION_BEHAVIOR_PROXIMITY_CURIOUS:
            pose.left_eye_length = 0.44f; pose.right_eye_length = 0.59f;
            pose.left_eye_thickness = 0.34f; pose.right_eye_thickness = 0.42f;
            pose.eye_open = 1.0f; pose.scale_x = 1.05f; pose.scale_y = 1.05f;
            pose.gaze_y = -0.12f;
            break;
        case COMPANION_BEHAVIOR_SHAKE_DIZZY:
            pose.left_eye_length = 0.48f; pose.right_eye_length = 0.48f;
            pose.left_eye_thickness = 0.14f; pose.right_eye_thickness = 0.14f;
            pose.left_eye_angle = -0.78f; pose.right_eye_angle = -0.78f;
            pose.particle_count = 8;
            break;
        case COMPANION_BEHAVIOR_DROWSY_SLEEP:
            pose.eye_open = 0.10f; pose.scale_x = 1.04f; pose.scale_y = 0.92f;
            pose.offset_y = 0.09f; pose.sleeping = true;
            break;
        case COMPANION_BEHAVIOR_NONE:
        default:
            break;
    }
    return pose;
}

companion_expression_layer_t companion_expression_engine_active_layer(
    const companion_expression_engine_t *engine, uint32_t now_ms)
{
    if (engine == NULL) return COMPANION_EXPRESSION_LAYER_IDLE;
    if (engine->updating) return COMPANION_EXPRESSION_LAYER_SYSTEM;
    if (system_is_high_priority(engine->system_state)) return COMPANION_EXPRESSION_LAYER_SYSTEM;
    if (engine->behavior != COMPANION_BEHAVIOR_NONE && now_ms < engine->behavior_expires_ms) {
        return COMPANION_EXPRESSION_LAYER_PHYSICAL;
    }
    if (system_is_interaction(engine->system_state)) return COMPANION_EXPRESSION_LAYER_INTERACTION;
    if (engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE && now_ms < engine->preview_expires_ms) {
        if (engine->preview == COMPANION_EXPRESSION_PREVIEW_BEHAVIOR) {
            return COMPANION_EXPRESSION_LAYER_PHYSICAL;
        }
        if (engine->preview == COMPANION_EXPRESSION_PREVIEW_EMOTION) {
            return COMPANION_EXPRESSION_LAYER_EMOTION;
        }
        return COMPANION_EXPRESSION_LAYER_INTERACTION;
    }
    if (engine->emotion != COMPANION_EMOTION_NEUTRAL && now_ms < engine->emotion_expires_ms) {
        return COMPANION_EXPRESSION_LAYER_EMOTION;
    }
    return COMPANION_EXPRESSION_LAYER_IDLE;
}

static uint32_t effective_key(const companion_expression_engine_t *engine, uint32_t now_ms)
{
    companion_expression_layer_t layer = companion_expression_engine_active_layer(engine, now_ms);
    uint32_t value = 0;
    bool previewing = engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE &&
                      now_ms < engine->preview_expires_ms &&
                      !engine->updating && !system_is_high_priority(engine->system_state) &&
                      !system_is_interaction(engine->system_state) &&
                      !(engine->behavior != COMPANION_BEHAVIOR_NONE && now_ms < engine->behavior_expires_ms);
    if (previewing) {
        value = 0x10000U | ((uint32_t)engine->preview << 8) | engine->preview_value;
    } else if (layer == COMPANION_EXPRESSION_LAYER_SYSTEM || layer == COMPANION_EXPRESSION_LAYER_INTERACTION) {
        value = engine->updating ? 0xffU : (uint32_t)engine->system_state;
    } else if (layer == COMPANION_EXPRESSION_LAYER_PHYSICAL) {
        value = (uint32_t)engine->behavior;
    } else if (layer == COMPANION_EXPRESSION_LAYER_EMOTION) {
        value = (uint32_t)engine->emotion;
    }
    return ((uint32_t)layer << 24) | value;
}

static companion_expression_pose_t effective_pose(const companion_expression_engine_t *engine,
                                                   uint32_t now_ms)
{
    companion_expression_layer_t layer = companion_expression_engine_active_layer(engine, now_ms);
    bool previewing = engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE &&
                      now_ms < engine->preview_expires_ms &&
                      !engine->updating && !system_is_high_priority(engine->system_state) &&
                      !system_is_interaction(engine->system_state) &&
                      !(engine->behavior != COMPANION_BEHAVIOR_NONE && now_ms < engine->behavior_expires_ms);
    if (previewing) {
        if (engine->preview == COMPANION_EXPRESSION_PREVIEW_EMOTION) {
            return emotion_pose((companion_emotion_t)engine->preview_value,
                                COMPANION_EMOTION_INTENSITY_STRONG);
        }
        if (engine->preview == COMPANION_EXPRESSION_PREVIEW_BEHAVIOR) {
            return behavior_pose((companion_expression_behavior_t)engine->preview_value);
        }
        if (engine->preview == COMPANION_EXPRESSION_PREVIEW_UPDATING) {
            companion_expression_pose_t pose = neutral_pose();
            pose.left_eye_length = 0.48f; pose.right_eye_length = 0.48f;
            pose.left_eye_thickness = 0.18f; pose.right_eye_thickness = 0.18f;
            pose.left_eye_angle = -0.42f; pose.right_eye_angle = 0.42f;
            pose.eye_spacing = 0.39f; pose.particle_count = 4; pose.orbit = 1.0f;
            pose.body_style = COMPANION_BODY_UPDATE;
            return pose;
        }
        return system_pose((companion_face_state_t)engine->preview_value);
    }
    if (layer == COMPANION_EXPRESSION_LAYER_SYSTEM || layer == COMPANION_EXPRESSION_LAYER_INTERACTION) {
        if (engine->updating) {
            companion_expression_pose_t pose = neutral_pose();
            pose.left_eye_length = 0.48f; pose.right_eye_length = 0.48f;
            pose.left_eye_thickness = 0.18f; pose.right_eye_thickness = 0.18f;
            pose.left_eye_angle = -0.42f; pose.right_eye_angle = 0.42f;
            pose.eye_spacing = 0.39f; pose.particle_count = 4; pose.orbit = 1.0f;
            pose.body_style = COMPANION_BODY_UPDATE;
            return pose;
        }
        return system_pose(engine->system_state);
    }
    if (layer == COMPANION_EXPRESSION_LAYER_PHYSICAL) return behavior_pose(engine->behavior);
    if (layer == COMPANION_EXPRESSION_LAYER_EMOTION) {
        return emotion_pose(engine->emotion, engine->intensity);
    }
    return neutral_pose();
}

static uint32_t transition_duration(companion_expression_layer_t layer, uint32_t key)
{
    if (layer == COMPANION_EXPRESSION_LAYER_SYSTEM || layer == COMPANION_EXPRESSION_LAYER_INTERACTION) {
        return 180U;
    }
    if (layer == COMPANION_EXPRESSION_LAYER_PHYSICAL) return 320U;
    if (layer == COMPANION_EXPRESSION_LAYER_EMOTION) {
        uint32_t value = key & 0xffU;
        return value == COMPANION_EMOTION_HAPPY || value == COMPANION_EMOTION_SURPRISED ? 520U : 360U;
    }
    return 800U;
}

static companion_expression_pose_t interpolate(companion_expression_pose_t from,
                                               companion_expression_pose_t to,
                                               float amount)
{
    companion_expression_pose_t pose = to;
#define LERP_FIELD(field) pose.field = lerpf(from.field, to.field, amount)
    LERP_FIELD(scale_x); LERP_FIELD(scale_y); LERP_FIELD(offset_x); LERP_FIELD(offset_y);
    LERP_FIELD(eye_open);
    LERP_FIELD(eye_spacing); LERP_FIELD(left_eye_length); LERP_FIELD(right_eye_length);
    LERP_FIELD(left_eye_thickness); LERP_FIELD(right_eye_thickness);
    LERP_FIELD(left_eye_angle); LERP_FIELD(right_eye_angle);
    LERP_FIELD(gaze_x); LERP_FIELD(gaze_y); LERP_FIELD(blush); LERP_FIELD(orbit);
#undef LERP_FIELD
    pose.particle_count = amount > 0.55f ? to.particle_count : from.particle_count;
    pose.body_style = amount > 0.55f ? to.body_style : from.body_style;
    pose.sleeping = amount > 0.75f ? to.sleeping : from.sleeping;
    return pose;
}

void companion_expression_engine_init(companion_expression_engine_t *engine, uint32_t now_ms)
{
    if (engine == NULL) return;
    memset(engine, 0, sizeof(*engine));
    engine->system_state = COMPANION_FACE_IDLE;
    engine->emotion = COMPANION_EMOTION_NEUTRAL;
    engine->intensity = COMPANION_EMOTION_INTENSITY_MEDIUM;
    engine->behavior = COMPANION_BEHAVIOR_BOOT_APPEAR;
    engine->behavior_expires_ms = now_ms + 1800U;
    engine->current = behavior_pose(engine->behavior);
    engine->transition_from = engine->current;
    engine->state_key = effective_key(engine, now_ms);
    engine->transition_started_ms = now_ms;
    engine->transition_duration_ms = 480U;
    engine->last_tick_ms = now_ms;
    engine->initialized = true;
}

void companion_expression_engine_set_system(companion_expression_engine_t *engine,
                                            companion_face_state_t state,
                                            uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || state < COMPANION_FACE_IDLE ||
        state > COMPANION_FACE_RECOVERABLE_ERROR) return;
    engine->system_state = state;
    engine->last_tick_ms = now_ms;
}

void companion_expression_engine_suggest_emotion(companion_expression_engine_t *engine,
                                                  companion_emotion_t emotion,
                                                  companion_emotion_intensity_t intensity,
                                                  uint32_t duration_ms,
                                                  uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || emotion >= COMPANION_EMOTION_COUNT ||
        intensity > COMPANION_EMOTION_INTENSITY_STRONG) return;
    if (duration_ms == 0) duration_ms = DEFAULT_EMOTION_MS;
    duration_ms = duration_ms < MIN_EMOTION_MS ? MIN_EMOTION_MS :
                  (duration_ms > MAX_EMOTION_MS ? MAX_EMOTION_MS : duration_ms);
    engine->emotion = emotion;
    engine->intensity = intensity;
    engine->emotion_expires_ms = now_ms + duration_ms;
}

void companion_expression_engine_trigger(companion_expression_engine_t *engine,
                                         companion_expression_behavior_t behavior,
                                         uint32_t duration_ms,
                                         uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || behavior > COMPANION_BEHAVIOR_DROWSY_SLEEP) return;
    if (behavior == COMPANION_BEHAVIOR_NONE) {
        engine->behavior = behavior;
        engine->behavior_expires_ms = now_ms;
        return;
    }
    if (duration_ms == 0) duration_ms = 2200U;
    duration_ms = duration_ms < MIN_BEHAVIOR_MS ? MIN_BEHAVIOR_MS :
                  (duration_ms > MAX_BEHAVIOR_MS ? MAX_BEHAVIOR_MS : duration_ms);
    engine->behavior = behavior;
    engine->behavior_expires_ms = now_ms + duration_ms;
}

void companion_expression_engine_set_updating(companion_expression_engine_t *engine,
                                              bool updating,
                                              uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized) return;
    engine->updating = updating;
    engine->last_tick_ms = now_ms;
}

void companion_expression_engine_preview(companion_expression_engine_t *engine,
                                         companion_expression_preview_t preview,
                                         uint8_t value,
                                         uint32_t duration_ms,
                                         uint32_t now_ms)
{
    bool value_valid = (preview == COMPANION_EXPRESSION_PREVIEW_EMOTION &&
                        value < COMPANION_EMOTION_COUNT) ||
                       (preview == COMPANION_EXPRESSION_PREVIEW_SYSTEM &&
                        value <= COMPANION_FACE_RECOVERABLE_ERROR) ||
                       (preview == COMPANION_EXPRESSION_PREVIEW_BEHAVIOR &&
                        value > COMPANION_BEHAVIOR_NONE &&
                        value <= COMPANION_BEHAVIOR_DROWSY_SLEEP) ||
                       preview == COMPANION_EXPRESSION_PREVIEW_UPDATING;
    if (engine == NULL || !engine->initialized || preview == COMPANION_EXPRESSION_PREVIEW_NONE ||
        preview > COMPANION_EXPRESSION_PREVIEW_UPDATING || duration_ms < 1000U ||
        duration_ms > 15000U || !value_valid) return;
    engine->preview = preview;
    engine->preview_value = value;
    engine->preview_expires_ms = now_ms + duration_ms;
}

void companion_expression_engine_tick(companion_expression_engine_t *engine,
                                      uint32_t now_ms,
                                      companion_expression_pose_t *pose)
{
    if (engine == NULL || pose == NULL || !engine->initialized) return;
    if (engine->emotion != COMPANION_EMOTION_NEUTRAL && now_ms >= engine->emotion_expires_ms) {
        engine->emotion = COMPANION_EMOTION_NEUTRAL;
    }
    if (engine->behavior != COMPANION_BEHAVIOR_NONE && now_ms >= engine->behavior_expires_ms) {
        engine->behavior = COMPANION_BEHAVIOR_NONE;
    }
    if (engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE && now_ms >= engine->preview_expires_ms) {
        engine->preview = COMPANION_EXPRESSION_PREVIEW_NONE;
    }
    uint32_t key = effective_key(engine, now_ms);
    companion_expression_layer_t layer = companion_expression_engine_active_layer(engine, now_ms);
    if (key != engine->state_key) {
        engine->transition_from = engine->current;
        engine->transition_started_ms = now_ms;
        engine->transition_duration_ms = transition_duration(layer, key);
        engine->state_key = key;
    }
    companion_expression_pose_t target = effective_pose(engine, now_ms);
    uint32_t elapsed = now_ms - engine->transition_started_ms;
    float amount = engine->transition_duration_ms == 0 ? 1.0f :
                   (float)elapsed / (float)engine->transition_duration_ms;
    engine->current = interpolate(engine->transition_from, target, smoothstep(amount));

    float seconds = (float)now_ms / 1000.0f;
    bool preview_active = engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE &&
                          now_ms < engine->preview_expires_ms;
    companion_face_state_t animated_system =
        preview_active && engine->preview == COMPANION_EXPRESSION_PREVIEW_SYSTEM
            ? (companion_face_state_t)engine->preview_value : engine->system_state;
    companion_expression_behavior_t animated_behavior =
        preview_active && engine->preview == COMPANION_EXPRESSION_PREVIEW_BEHAVIOR
            ? (companion_expression_behavior_t)engine->preview_value : engine->behavior;
    companion_emotion_t animated_emotion =
        preview_active && engine->preview == COMPANION_EXPRESSION_PREVIEW_EMOTION
            ? (companion_emotion_t)engine->preview_value : engine->emotion;
    if (engine->updating || (preview_active &&
        engine->preview == COMPANION_EXPRESSION_PREVIEW_UPDATING)) {
        engine->current.gaze_x += sinf(seconds * 4.0f) * 0.18f;
    } else if (layer == COMPANION_EXPRESSION_LAYER_IDLE ||
               animated_behavior == COMPANION_BEHAVIOR_IDLE_BREATHE) {
        float breathe = sinf(seconds * 2.2f) * 0.018f;
        engine->current.scale_x += breathe;
        engine->current.scale_y -= breathe * 0.7f;
        engine->current.offset_y += sinf(seconds * 1.1f) * 0.018f;
        float blink_phase = fmodf(seconds, 4.2f);
        if (blink_phase > 3.92f) {
            float blink = fabsf(blink_phase - 4.06f) / 0.14f;
            engine->current.eye_open *= clampf(blink, 0.08f, 1.0f);
        }
    } else if (animated_system == COMPANION_FACE_PROCESSING) {
        engine->current.gaze_x += sinf(seconds * 5.4f) * 0.22f;
    } else if (animated_system == COMPANION_FACE_SPEAKING) {
        engine->current.scale_x += sinf(seconds * 8.0f) * 0.035f;
        engine->current.scale_y -= sinf(seconds * 8.0f) * 0.025f;
    } else if (animated_behavior == COMPANION_BEHAVIOR_SHAKE_DIZZY &&
               (preview_active || now_ms < engine->behavior_expires_ms)) {
        engine->current.gaze_x += sinf(seconds * 13.0f) * 0.30f;
        engine->current.offset_x += sinf(seconds * 17.0f) * 0.035f;
        engine->current.left_eye_angle += sinf(seconds * 9.0f) * 0.18f;
        engine->current.right_eye_angle -= sinf(seconds * 9.0f) * 0.18f;
    }
    if (layer == COMPANION_EXPRESSION_LAYER_EMOTION &&
        animated_emotion == COMPANION_EMOTION_NERVOUS) {
        engine->current.offset_x += sinf(seconds * 19.0f) * 0.018f;
        engine->current.gaze_y += cosf(seconds * 17.0f) * 0.035f;
    } else if (layer == COMPANION_EXPRESSION_LAYER_EMOTION &&
               animated_emotion == COMPANION_EMOTION_ANGRY) {
        engine->current.offset_x += sinf(seconds * 23.0f) * 0.010f;
    }
    engine->last_tick_ms = now_ms;
    *pose = engine->current;
}

const char *companion_expression_layer_name(companion_expression_layer_t layer)
{
    switch (layer) {
        case COMPANION_EXPRESSION_LAYER_SYSTEM: return "SYSTEM";
        case COMPANION_EXPRESSION_LAYER_PHYSICAL: return "PHYSICAL";
        case COMPANION_EXPRESSION_LAYER_INTERACTION: return "INTERACTION";
        case COMPANION_EXPRESSION_LAYER_EMOTION: return "EMOTION";
        case COMPANION_EXPRESSION_LAYER_IDLE:
        default: return "IDLE";
    }
}

bool companion_emotion_parse(const char *value, companion_emotion_t *emotion)
{
    static const char *const names[] = {
        "NEUTRAL", "HAPPY", "LOVING", "SAD", "ANGRY", "SURPRISED",
        "CONFUSED", "SHY", "TIRED", "FOCUSED", "NERVOUS", "CONTENT"
    };
    if (value == NULL || emotion == NULL) return false;
    for (size_t index = 0; index < sizeof(names) / sizeof(names[0]); index++) {
        if (strcmp(value, names[index]) == 0) {
            *emotion = (companion_emotion_t)index;
            return true;
        }
    }
    return false;
}

bool companion_emotion_intensity_parse(const char *value,
                                       companion_emotion_intensity_t *intensity)
{
    if (value == NULL || intensity == NULL) return false;
    if (strcmp(value, "WEAK") == 0) *intensity = COMPANION_EMOTION_INTENSITY_WEAK;
    else if (strcmp(value, "MEDIUM") == 0) *intensity = COMPANION_EMOTION_INTENSITY_MEDIUM;
    else if (strcmp(value, "STRONG") == 0) *intensity = COMPANION_EMOTION_INTENSITY_STRONG;
    else return false;
    return true;
}

bool companion_expression_system_parse(const char *value, companion_face_state_t *state,
                                       bool *updating)
{
    static const char *const names[] = {
        "IDLE", "LISTENING", "PROCESSING", "SPEAKING", "SUCCESS", "NO_SPEECH",
        "OFFLINE", "RECOVERABLE_ERROR"
    };
    if (value == NULL || state == NULL || updating == NULL) return false;
    *updating = false;
    if (strcmp(value, "UPDATING") == 0) {
        *state = COMPANION_FACE_IDLE;
        *updating = true;
        return true;
    }
    for (size_t index = 0; index < sizeof(names) / sizeof(names[0]); index++) {
        if (strcmp(value, names[index]) == 0) {
            *state = (companion_face_state_t)index;
            return true;
        }
    }
    return false;
}

bool companion_expression_behavior_parse(const char *value,
                                         companion_expression_behavior_t *behavior)
{
    static const char *const names[] = {
        "NONE", "BOOT_APPEAR", "WAKE", "IDLE_BREATHE", "PROXIMITY_CURIOUS",
        "SHAKE_DIZZY", "DROWSY_SLEEP"
    };
    if (value == NULL || behavior == NULL) return false;
    for (size_t index = 1; index < sizeof(names) / sizeof(names[0]); index++) {
        if (strcmp(value, names[index]) == 0) {
            *behavior = (companion_expression_behavior_t)index;
            return true;
        }
    }
    return false;
}
