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

/* Lifetimes are below INT32_MAX, so expiry also works across timer wrap. */
static bool before(uint32_t now_ms, uint32_t deadline_ms)
{
    return (int32_t)(now_ms - deadline_ms) < 0;
}

static uint32_t random_next(companion_expression_engine_t *engine)
{
    uint32_t value = engine->random_state;
    value ^= value << 13;
    value ^= value >> 17;
    value ^= value << 5;
    engine->random_state = value;
    return value;
}

static companion_expression_pose_t neutral_pose(void)
{
    return (companion_expression_pose_t){
        .scale_x = 1.0f,
        .scale_y = 1.0f,
        .eye_open = 1.0f,
        .left_eye_open = 1.0f,
        .right_eye_open = 1.0f,
        .eye_spacing = 0.46f,
        .left_eye_length = 0.62f,
        .right_eye_length = 0.62f,
        .left_eye_thickness = 0.29f,
        .right_eye_thickness = 0.29f,
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
            pose.left_eye_length = pose.right_eye_length = 0.69f;
            pose.left_lower_lid = pose.right_lower_lid = 0.63f * amount;
            pose.left_lower_curve = pose.right_lower_curve = 0.95f;
            pose.blush = amount * 0.18f;
            break;
        case COMPANION_EMOTION_LOVING:
            pose.left_eye_length = pose.right_eye_length = 0.55f;
            pose.left_lower_lid = pose.right_lower_lid = 0.74f * amount;
            pose.left_lower_curve = pose.right_lower_curve = 0.80f;
            pose.eye_spacing = 0.42f; pose.blush = amount;
            pose.body_style = COMPANION_BODY_BLUSH;
            break;
        case COMPANION_EMOTION_SAD:
            pose.left_upper_lid = pose.right_upper_lid = 0.40f * amount;
            pose.left_eye_angle = -0.50f * amount; pose.right_eye_angle = 0.50f * amount;
            pose.left_eye_thickness = pose.right_eye_thickness = 0.26f;
            pose.gaze_y = 0.22f;
            break;
        case COMPANION_EMOTION_ANGRY:
            pose.left_upper_lid = pose.right_upper_lid = 0.35f * amount;
            pose.left_eye_angle = 0.63f * amount; pose.right_eye_angle = -0.63f * amount;
            pose.eye_spacing = 0.41f;
            pose.body_style = COMPANION_BODY_CORAL;
            break;
        case COMPANION_EMOTION_SURPRISED:
            pose.left_eye_length = pose.right_eye_length = 0.52f;
            pose.left_eye_thickness = pose.right_eye_thickness = 0.42f;
            pose.left_eye_open = pose.right_eye_open = 1.10f;
            pose.eye_spacing = 0.49f;
            break;
        case COMPANION_EMOTION_CONFUSED:
            pose.left_eye_length = 0.47f; pose.right_eye_length = 0.66f;
            pose.left_eye_thickness = 0.23f; pose.right_eye_thickness = 0.36f;
            pose.left_upper_lid = 0.42f * amount;
            pose.left_eye_angle = -0.40f; pose.gaze_x = 0.12f;
            break;
        case COMPANION_EMOTION_SHY:
            pose.left_eye_length = pose.right_eye_length = 0.50f;
            pose.left_upper_lid = pose.right_upper_lid = 0.28f * amount;
            pose.left_lower_lid = pose.right_lower_lid = 0.20f * amount;
            pose.gaze_x = -0.26f; pose.gaze_y = 0.24f; pose.blush = amount;
            pose.body_style = COMPANION_BODY_BLUSH;
            break;
        case COMPANION_EMOTION_TIRED:
            pose.left_upper_lid = 0.72f * amount; pose.right_upper_lid = 0.62f * amount;
            pose.left_eye_open = 0.77f; pose.right_eye_open = 0.82f;
            pose.gaze_y = 0.18f;
            break;
        case COMPANION_EMOTION_FOCUSED:
            pose.left_upper_lid = pose.right_upper_lid = 0.46f * amount;
            pose.left_eye_angle = 0.16f; pose.right_eye_angle = -0.16f;
            pose.left_eye_length = pose.right_eye_length = 0.59f;
            pose.eye_spacing = 0.40f;
            break;
        case COMPANION_EMOTION_NERVOUS:
            pose.left_eye_length = 0.37f; pose.right_eye_length = 0.41f;
            pose.left_eye_thickness = 0.35f; pose.right_eye_thickness = 0.38f;
            pose.left_upper_lid = 0.18f * amount;
            pose.eye_spacing = 0.45f; pose.gaze_x = 0.11f;
            break;
        case COMPANION_EMOTION_CONTENT:
            pose.left_lower_lid = pose.right_lower_lid = 0.31f * amount;
            pose.left_lower_curve = pose.right_lower_curve = 0.64f;
            pose.left_upper_lid = pose.right_upper_lid = 0.09f;
            pose.blush = amount * 0.12f;
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
            pose.left_eye_thickness = pose.right_eye_thickness = 0.36f;
            pose.eye_spacing = 0.43f;
            break;
        case COMPANION_FACE_PROCESSING:
            pose = emotion_pose(COMPANION_EMOTION_FOCUSED, COMPANION_EMOTION_INTENSITY_MEDIUM);
            pose.right_upper_lid *= 0.45f;
            break;
        case COMPANION_FACE_SPEAKING:
            pose.left_eye_length = 0.62f; pose.right_eye_length = 0.62f;
            pose.left_eye_thickness = 0.27f; pose.right_eye_thickness = 0.27f;
            pose.scale_x = 1.04f; pose.scale_y = 0.97f;
            break;
        case COMPANION_FACE_SUCCESS:
            pose = emotion_pose(COMPANION_EMOTION_HAPPY, COMPANION_EMOTION_INTENSITY_STRONG);
            break;
        case COMPANION_FACE_NO_SPEECH:
            pose = emotion_pose(COMPANION_EMOTION_CONFUSED, COMPANION_EMOTION_INTENSITY_MEDIUM);
            break;
        case COMPANION_FACE_OFFLINE:
            pose.eye_open = 0.42f;
            pose.left_upper_lid = pose.right_upper_lid = 0.50f;
            pose.body_style = COMPANION_BODY_MUTED;
            break;
        case COMPANION_FACE_RECOVERABLE_ERROR:
            pose = emotion_pose(COMPANION_EMOTION_ANGRY, COMPANION_EMOTION_INTENSITY_STRONG);
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
            pose.left_upper_lid = 0.40f; pose.right_lower_lid = 0.40f;
            pose.left_eye_angle = 0.40f; pose.right_eye_angle = 0.40f;
            break;
        case COMPANION_BEHAVIOR_DROWSY_SLEEP:
            pose.eye_open = 0.10f; pose.scale_x = 1.04f; pose.scale_y = 0.92f;
            pose.offset_y = 0.09f; pose.sleeping = true;
            break;
        case COMPANION_BEHAVIOR_ROLE_SWITCH:
            pose.left_eye_open = 0.08f; pose.right_eye_open = 0.90f;
            pose.gaze_x = 0.15f;
            break;
        case COMPANION_BEHAVIOR_NONE:
        default:
            break;
    }
    return pose;
}

static bool emotion_is_visible(const companion_expression_engine_t *engine, uint32_t now_ms)
{
    return !engine->turn_emotion_pending && before(now_ms, engine->emotion_expires_ms) &&
           (engine->emotion != COMPANION_EMOTION_NEUTRAL || engine->turn_emotion_received);
}

companion_expression_layer_t companion_expression_engine_active_layer(
    const companion_expression_engine_t *engine, uint32_t now_ms)
{
    if (engine == NULL) return COMPANION_EXPRESSION_LAYER_IDLE;
    if (engine->updating) return COMPANION_EXPRESSION_LAYER_SYSTEM;
    if (system_is_high_priority(engine->system_state)) return COMPANION_EXPRESSION_LAYER_SYSTEM;
    if (system_is_interaction(engine->system_state)) return COMPANION_EXPRESSION_LAYER_INTERACTION;
    if (engine->behavior != COMPANION_BEHAVIOR_NONE && before(now_ms, engine->behavior_expires_ms)) {
        return COMPANION_EXPRESSION_LAYER_PHYSICAL;
    }
    if (engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE && before(now_ms, engine->preview_expires_ms)) {
        if (engine->preview == COMPANION_EXPRESSION_PREVIEW_BEHAVIOR) {
            return COMPANION_EXPRESSION_LAYER_PHYSICAL;
        }
        if (engine->preview == COMPANION_EXPRESSION_PREVIEW_EMOTION) {
            return COMPANION_EXPRESSION_LAYER_EMOTION;
        }
        return COMPANION_EXPRESSION_LAYER_INTERACTION;
    }
    if ((engine->body_emotion != COMPANION_EMOTION_NEUTRAL && before(now_ms, engine->body_emotion_expires_ms)) ||
        emotion_is_visible(engine, now_ms)) {
        return COMPANION_EXPRESSION_LAYER_EMOTION;
    }
    return COMPANION_EXPRESSION_LAYER_IDLE;
}

static bool preview_is_visible(const companion_expression_engine_t *engine, uint32_t now_ms)
{
    return engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE &&
           before(now_ms, engine->preview_expires_ms) && !engine->updating &&
           !system_is_high_priority(engine->system_state) &&
           !system_is_interaction(engine->system_state) &&
           !(engine->behavior != COMPANION_BEHAVIOR_NONE && before(now_ms, engine->behavior_expires_ms));
}

static uint32_t effective_key(const companion_expression_engine_t *engine, uint32_t now_ms)
{
    companion_expression_layer_t layer = companion_expression_engine_active_layer(engine, now_ms);
    uint32_t value = 0;
    bool previewing = preview_is_visible(engine, now_ms);
    if (previewing) {
        value = 0x10000U | ((uint32_t)engine->preview << 8) | engine->preview_value;
    } else if (layer == COMPANION_EXPRESSION_LAYER_SYSTEM || layer == COMPANION_EXPRESSION_LAYER_INTERACTION) {
        value = engine->updating ? 0xffU : (uint32_t)engine->system_state;
        if (engine->system_state == COMPANION_FACE_SPEAKING && emotion_is_visible(engine, now_ms)) {
            value |= 0x1000U | ((uint32_t)engine->emotion << 8) | ((uint32_t)engine->intensity << 16);
        }
    } else if (layer == COMPANION_EXPRESSION_LAYER_PHYSICAL) {
        value = (uint32_t)engine->behavior;
    } else if (layer == COMPANION_EXPRESSION_LAYER_EMOTION) {
        value = emotion_is_visible(engine, now_ms) ? (uint32_t)engine->emotion | ((uint32_t)engine->intensity << 16)
                    : 0x20000U | (uint32_t)engine->body_emotion;
    }
    return ((uint32_t)layer << 24) | value;
}

static companion_expression_pose_t effective_pose(const companion_expression_engine_t *engine,
                                                   uint32_t now_ms)
{
    companion_expression_layer_t layer = companion_expression_engine_active_layer(engine, now_ms);
    bool previewing = preview_is_visible(engine, now_ms);
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
            pose.eye_spacing = 0.39f; pose.left_upper_lid = pose.right_upper_lid = 0.25f;
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
            pose.eye_spacing = 0.39f; pose.left_upper_lid = pose.right_upper_lid = 0.25f;
            pose.body_style = COMPANION_BODY_UPDATE;
            return pose;
        }
        if (engine->system_state == COMPANION_FACE_SPEAKING && emotion_is_visible(engine, now_ms)) {
            return emotion_pose(engine->emotion, engine->intensity);
        }
        return system_pose(engine->system_state);
    }
    if (layer == COMPANION_EXPRESSION_LAYER_PHYSICAL) return behavior_pose(engine->behavior);
    if (layer == COMPANION_EXPRESSION_LAYER_EMOTION) {
        return emotion_is_visible(engine, now_ms) ? emotion_pose(engine->emotion, engine->intensity)
            : emotion_pose(engine->body_emotion, COMPANION_EMOTION_INTENSITY_WEAK);
    }
    return neutral_pose();
}

static uint32_t transition_duration(companion_expression_layer_t layer, uint32_t key)
{
    if (layer == COMPANION_EXPRESSION_LAYER_SYSTEM) return 0U;
    if (layer == COMPANION_EXPRESSION_LAYER_INTERACTION) {
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
    LERP_FIELD(left_eye_open); LERP_FIELD(right_eye_open);
    LERP_FIELD(left_upper_lid); LERP_FIELD(right_upper_lid);
    LERP_FIELD(left_lower_lid); LERP_FIELD(right_lower_lid);
    LERP_FIELD(left_lower_curve); LERP_FIELD(right_lower_curve);
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
    engine->behavior_started_ms = now_ms;
    engine->behavior_expires_ms = now_ms + 1800U;
    engine->current = behavior_pose(engine->behavior);
    engine->transition_from = engine->current;
    engine->state_key = effective_key(engine, now_ms);
    engine->transition_started_ms = now_ms;
    engine->transition_duration_ms = 480U;
    engine->last_tick_ms = now_ms;
    engine->random_state = 0x7ac19e53U ^ now_ms;
    if (engine->random_state == 0) engine->random_state = 1;
    engine->next_blink_ms = now_ms + 2400U + random_next(engine) % 2000U;
    engine->next_gaze_ms = now_ms + 1000U + random_next(engine) % 1800U;
    engine->initialized = true;
}

void companion_expression_engine_clear_turn(companion_expression_engine_t *engine, uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized) return;
    engine->turn_active = false;
    engine->turn_playback_started = false;
    engine->turn_emotion_received = false;
    engine->turn_emotion_pending = false;
    engine->turn_playback_started_ms = 0;
    engine->turn_emotion_duration_ms = 0;
    engine->emotion = COMPANION_EMOTION_NEUTRAL;
    engine->emotion_expires_ms = now_ms;
    /* Retain the last ID as a tombstone: duplicate begin cannot revive it. */
}

static bool valid_turn_id(const char *turn_id)
{
    if (turn_id == NULL || *turn_id == '\0') return false;
    size_t size = 0;
    while (size < COMPANION_EXPRESSION_TURN_ID_SIZE && turn_id[size] != '\0') size++;
    return size < COMPANION_EXPRESSION_TURN_ID_SIZE;
}

static bool matches_turn(const companion_expression_engine_t *engine, const char *turn_id)
{
    return engine != NULL && engine->initialized && engine->turn_active &&
           valid_turn_id(turn_id) && strcmp(turn_id, engine->turn_id) == 0;
}

bool companion_expression_engine_turn_begin(companion_expression_engine_t *engine,
                                             const char *turn_id, uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || !valid_turn_id(turn_id) ||
        strcmp(turn_id, engine->turn_id) == 0) return false;
    companion_expression_engine_clear_turn(engine, now_ms);
    memcpy(engine->turn_id, turn_id, strlen(turn_id) + 1);
    engine->turn_active = true;
    return true;
}

bool companion_expression_engine_turn_emotion(companion_expression_engine_t *engine,
                                               const char *turn_id, companion_emotion_t emotion,
                                               companion_emotion_intensity_t intensity,
                                               uint32_t duration_ms, uint32_t now_ms)
{
    if (!matches_turn(engine, turn_id) || engine->turn_emotion_received ||
        emotion < COMPANION_EMOTION_NEUTRAL || emotion >= COMPANION_EMOTION_COUNT ||
        intensity < COMPANION_EMOTION_INTENSITY_WEAK || intensity > COMPANION_EMOTION_INTENSITY_STRONG ||
        duration_ms < MIN_EMOTION_MS || duration_ms > MAX_EMOTION_MS) return false;
    engine->turn_emotion_received = true;
    engine->turn_emotion_duration_ms = duration_ms;
    engine->turn_emotion_pending = !engine->turn_playback_started;
    engine->emotion = emotion;
    engine->intensity = intensity;
    engine->emotion_expires_ms = engine->turn_playback_started
        ? engine->turn_playback_started_ms + duration_ms : now_ms;
    /* A command received after its original playback expiry cannot revive it. */
    if (!engine->turn_emotion_pending && !before(now_ms, engine->emotion_expires_ms))
        engine->emotion = COMPANION_EMOTION_NEUTRAL;
    return true;
}

bool companion_expression_engine_turn_playback_started(companion_expression_engine_t *engine,
                                                        const char *turn_id, uint32_t now_ms)
{
    if (!matches_turn(engine, turn_id) || engine->turn_playback_started) return false;
    engine->turn_playback_started = true;
    engine->turn_playback_started_ms = now_ms;
    if (engine->turn_emotion_pending) {
        engine->turn_emotion_pending = false;
        engine->emotion_expires_ms = now_ms + engine->turn_emotion_duration_ms;
    }
    return true;
}

bool companion_expression_engine_turn_end(companion_expression_engine_t *engine,
                                           const char *turn_id, uint32_t now_ms)
{
    if (!matches_turn(engine, turn_id)) return false;
    engine->turn_active = false;
    if (!engine->turn_playback_started) companion_expression_engine_clear_turn(engine, now_ms);
    return true;
}

bool companion_expression_engine_turn_cancel(companion_expression_engine_t *engine,
                                              const char *turn_id, uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || !valid_turn_id(turn_id) ||
        strcmp(turn_id, engine->turn_id) != 0 ||
        (!engine->turn_active && !engine->turn_emotion_received)) return false;
    companion_expression_engine_clear_turn(engine, now_ms);
    return true;
}

void companion_expression_engine_set_system(companion_expression_engine_t *engine,
                                            companion_face_state_t state,
                                            uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || state < COMPANION_FACE_IDLE ||
        state > COMPANION_FACE_RECOVERABLE_ERROR) return;
    engine->system_state = state;
    if (system_is_high_priority(state) && (engine->turn_active || engine->turn_emotion_received))
        companion_expression_engine_clear_turn(engine, now_ms);
    engine->last_tick_ms = now_ms;
}

void companion_expression_engine_suggest_emotion(companion_expression_engine_t *engine,
                                                  companion_emotion_t emotion,
                                                  companion_emotion_intensity_t intensity,
                                                  uint32_t duration_ms,
                                                  uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || emotion < COMPANION_EMOTION_NEUTRAL ||
        emotion >= COMPANION_EMOTION_COUNT || intensity < COMPANION_EMOTION_INTENSITY_WEAK ||
        intensity > COMPANION_EMOTION_INTENSITY_STRONG) return;
    if (engine->turn_active && engine->turn_emotion_received) return;
    if (duration_ms == 0) duration_ms = DEFAULT_EMOTION_MS;
    duration_ms = duration_ms < MIN_EMOTION_MS ? MIN_EMOTION_MS :
                  (duration_ms > MAX_EMOTION_MS ? MAX_EMOTION_MS : duration_ms);
    engine->emotion = emotion;
    engine->intensity = intensity;
    /* Legacy and passive suggestions remain immediate bounded effects. */
    engine->turn_emotion_pending = false;
    engine->turn_emotion_received = false;
    engine->emotion_expires_ms = now_ms + duration_ms;
}

void companion_expression_engine_trigger(companion_expression_engine_t *engine,
                                         companion_expression_behavior_t behavior,
                                         uint32_t duration_ms,
                                         uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || behavior > COMPANION_BEHAVIOR_ROLE_SWITCH) return;
    if (behavior == COMPANION_BEHAVIOR_NONE) {
        engine->behavior = behavior;
        engine->behavior_expires_ms = now_ms;
        return;
    }
    if (duration_ms == 0) duration_ms = 2200U;
    duration_ms = duration_ms < MIN_BEHAVIOR_MS ? MIN_BEHAVIOR_MS :
                  (duration_ms > MAX_BEHAVIOR_MS ? MAX_BEHAVIOR_MS : duration_ms);
    engine->behavior = behavior;
    engine->behavior_started_ms = now_ms;
    engine->behavior_expires_ms = now_ms + duration_ms;
}

void companion_expression_engine_set_body_emotion(companion_expression_engine_t *engine,
                                                  companion_emotion_t emotion, uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized || emotion >= COMPANION_EMOTION_COUNT) return;
    engine->body_emotion = emotion;
    engine->body_emotion_expires_ms = now_ms + 8000U;
}

void companion_expression_engine_set_updating(companion_expression_engine_t *engine,
                                              bool updating,
                                              uint32_t now_ms)
{
    if (engine == NULL || !engine->initialized) return;
    engine->updating = updating;
    if (updating && (engine->turn_active || engine->turn_emotion_received))
        companion_expression_engine_clear_turn(engine, now_ms);
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
                        value <= COMPANION_BEHAVIOR_ROLE_SWITCH) ||
                       preview == COMPANION_EXPRESSION_PREVIEW_UPDATING;
    if (engine == NULL || !engine->initialized || preview == COMPANION_EXPRESSION_PREVIEW_NONE ||
        preview > COMPANION_EXPRESSION_PREVIEW_UPDATING || duration_ms < 1000U ||
        duration_ms > 15000U || !value_valid) return;
    engine->preview = preview;
    engine->preview_value = value;
    engine->preview_expires_ms = now_ms + duration_ms;
}

static void idle_motion(companion_expression_engine_t *engine, uint32_t now_ms,
                        companion_expression_pose_t *pose)
{
    if (!before(now_ms, engine->next_gaze_ms)) {
        engine->idle_gaze_from_x = engine->idle_gaze_target_x;
        engine->idle_gaze_from_y = engine->idle_gaze_target_y;
        engine->idle_gaze_target_x = ((float)(random_next(engine) % 101U) - 50.0f) * 0.006f;
        engine->idle_gaze_target_y = ((float)(random_next(engine) % 101U) - 50.0f) * 0.003f;
        engine->gaze_started_ms = now_ms;
        engine->next_gaze_ms = now_ms + 1600U + random_next(engine) % 2600U;
    }
    float amount = smoothstep((float)(now_ms - engine->gaze_started_ms) / 360.0f);
    pose->gaze_x += lerpf(engine->idle_gaze_from_x, engine->idle_gaze_target_x, amount);
    pose->gaze_y += lerpf(engine->idle_gaze_from_y, engine->idle_gaze_target_y, amount);
    if (!engine->blinking && !before(now_ms, engine->next_blink_ms)) {
        engine->blinking = true;
        engine->blink_started_ms = now_ms;
    }
    if (engine->blinking) {
        uint32_t elapsed = now_ms - engine->blink_started_ms;
        if (elapsed >= 180U) {
            engine->blinking = false;
            engine->next_blink_ms = now_ms + 2600U + random_next(engine) % 3000U;
        } else {
            float opening = clampf(fabsf((float)elapsed - 90.0f) / 90.0f, 0.02f, 1.0f);
            pose->left_eye_open *= opening;
            pose->right_eye_open *= opening;
        }
    }
}

void companion_expression_engine_tick(companion_expression_engine_t *engine,
                                      uint32_t now_ms,
                                      companion_expression_pose_t *pose)
{
    if (engine == NULL || pose == NULL || !engine->initialized) return;
    if (!engine->turn_emotion_pending && !before(now_ms, engine->emotion_expires_ms)) {
        engine->emotion = COMPANION_EMOTION_NEUTRAL;
    }
    if (engine->behavior != COMPANION_BEHAVIOR_NONE && !before(now_ms, engine->behavior_expires_ms)) {
        engine->behavior = COMPANION_BEHAVIOR_NONE;
    }
    if (engine->preview != COMPANION_EXPRESSION_PREVIEW_NONE && !before(now_ms, engine->preview_expires_ms)) {
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
    /* Keep interpolation's base free of periodic motion, avoiding drift when
     * frame cadence changes. Animation is applied only to this output pose. */
    *pose = engine->current;

    float seconds = (float)now_ms / 1000.0f;
    bool preview_active = preview_is_visible(engine, now_ms);
    companion_face_state_t animated_system =
        preview_active && engine->preview == COMPANION_EXPRESSION_PREVIEW_SYSTEM
            ? (companion_face_state_t)engine->preview_value : engine->system_state;
    companion_expression_behavior_t animated_behavior =
        preview_active && engine->preview == COMPANION_EXPRESSION_PREVIEW_BEHAVIOR
            ? (companion_expression_behavior_t)engine->preview_value : engine->behavior;
    companion_emotion_t animated_emotion =
        preview_active && engine->preview == COMPANION_EXPRESSION_PREVIEW_EMOTION
            ? (companion_emotion_t)engine->preview_value
            : emotion_is_visible(engine, now_ms) ? engine->emotion : engine->body_emotion;
    if (engine->updating || (preview_active &&
        engine->preview == COMPANION_EXPRESSION_PREVIEW_UPDATING)) {
        pose->gaze_x += sinf(seconds * 4.0f) * 0.18f;
    } else if (layer == COMPANION_EXPRESSION_LAYER_IDLE ||
               (layer == COMPANION_EXPRESSION_LAYER_PHYSICAL &&
                animated_behavior == COMPANION_BEHAVIOR_IDLE_BREATHE)) {
        float breathe = sinf(seconds * 2.2f) * 0.018f;
        pose->scale_x += breathe;
        pose->scale_y -= breathe * 0.7f;
        pose->offset_y += sinf(seconds * 1.1f) * 0.018f;
        idle_motion(engine, now_ms, pose);
    } else if (layer == COMPANION_EXPRESSION_LAYER_INTERACTION &&
               animated_system == COMPANION_FACE_LISTENING) {
        pose->offset_y += sinf(seconds * 1.8f) * 0.075f;
        pose->left_eye_open *= 1.0f + sinf(seconds * 2.2f) * 0.065f;
        pose->right_eye_open *= 1.0f + sinf(seconds * 2.2f) * 0.065f;
        pose->orbit = (float)(now_ms % 1100U) / 1100.0f;
    } else if (layer == COMPANION_EXPRESSION_LAYER_INTERACTION &&
               animated_system == COMPANION_FACE_PROCESSING) {
        /* A glance pauses at each side, rather than oscillating continuously. */
        float phase = (float)(now_ms % 2400U) / 1200.0f;
        float travel = smoothstep(clampf(fmodf(phase, 1.0f) / 0.45f, 0, 1));
        pose->gaze_x += (phase < 1.0f ? -1.0f + 2.0f * travel : 1.0f - 2.0f * travel) * 0.40f;
        pose->orbit = (float)(now_ms % 1200U) / 1200.0f;
    } else if (layer == COMPANION_EXPRESSION_LAYER_INTERACTION &&
               animated_system == COMPANION_FACE_SPEAKING) {
        pose->offset_y += sinf(seconds * 7.5f) * 0.095f;
        pose->left_eye_open *= 1.0f + sinf(seconds * 8.0f) * 0.13f;
        pose->right_eye_open *= 1.0f + sinf(seconds * 8.0f + 0.3f) * 0.13f;
        pose->orbit = (float)(now_ms % 800U) / 800.0f;
    } else if (layer == COMPANION_EXPRESSION_LAYER_PHYSICAL &&
               animated_behavior == COMPANION_BEHAVIOR_SHAKE_DIZZY &&
               (preview_active || before(now_ms, engine->behavior_expires_ms))) {
        pose->gaze_x += sinf(seconds * 13.0f) * 0.30f;
        pose->offset_x += sinf(seconds * 17.0f) * 0.035f;
        pose->left_eye_angle += sinf(seconds * 9.0f) * 0.18f;
        pose->right_eye_angle -= sinf(seconds * 9.0f) * 0.18f;
    }
    if (layer == COMPANION_EXPRESSION_LAYER_EMOTION) {
        switch (animated_emotion) {
            case COMPANION_EMOTION_HAPPY:
            case COMPANION_EMOTION_CONTENT: {
                /* Two little laugh bounces followed by a quiet hold. */
                float phase = (float)(now_ms % 2400U) / 600.0f;
                float bounce = phase < 1.0f ? sinf(phase * 6.2831853f) : 0;
                pose->offset_y += bounce * 0.12f;
                pose->scale_y -= fabsf(bounce) * 0.08f;
                break;
            }
            case COMPANION_EMOTION_LOVING:
                pose->offset_x += sinf(seconds * 2.1f) * 0.10f;
                pose->offset_y += cosf(seconds * 4.2f) * 0.055f;
                break;
            case COMPANION_EMOTION_CONFUSED:
                pose->gaze_x += sinf(seconds * 3.2f) * 0.30f;
                pose->right_eye_open *= 1.0f + sinf(seconds * 3.2f) * 0.14f;
                break;
            case COMPANION_EMOTION_NERVOUS:
                pose->offset_x += sinf(seconds * 19.0f) * 0.06f;
                pose->gaze_y += cosf(seconds * 17.0f) * 0.055f;
                break;
            case COMPANION_EMOTION_ANGRY:
                pose->offset_x += sinf(seconds * 12.0f) * 0.04f;
                break;
            default: break;
        }
        idle_motion(engine, now_ms, pose);
    }
    /* Recording outranks physical expressions. Preserve one visible wake
     * response without weakening system/error priority or renewing its TTL. */
    if (!engine->updating && layer == COMPANION_EXPRESSION_LAYER_INTERACTION &&
        animated_system == COMPANION_FACE_LISTENING &&
        animated_behavior == COMPANION_BEHAVIOR_WAKE) {
        uint32_t wake_elapsed = now_ms - engine->behavior_started_ms;
        if (wake_elapsed < 480U) {
            float kick = sinf((float)wake_elapsed * 3.1415927f / 480.0f);
            pose->scale_y += kick * 0.18f;
            pose->scale_x -= kick * 0.09f;
            pose->offset_y -= kick * 0.18f;
        }
    }
    engine->last_tick_ms = now_ms;
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
        "SHAKE_DIZZY", "DROWSY_SLEEP", "ROLE_SWITCH"
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
