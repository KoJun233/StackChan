#include "companion_hardware.h"

#include <cinttypes>
#include <cmath>
#include <cstdlib>
#include <cstring>

#include "audio_wav.h"
#include "bsp/esp-bsp.h"
#include "driver/i2s_std.h"
#include "esp_codec_dev.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "expression_engine.h"
#include "expression_pack.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "src/misc/cache/instance/lv_image_cache.h"

#define UI_TASK_STACK_SIZE 6144
#define UI_TASK_PRIORITY 2
#define UI_TASK_CORE 1
#define UI_STARTUP_GRACE_MS 1000
#define UI_TIMER_MIN_WAIT_US 200
#define INPUT_POLL_MS 10
#define IMU_SAMPLE_MS 40
#define TOUCH_EVENT_QUEUE_LENGTH 8
#define NORMAL_BRIGHTNESS_PERCENT 63
#define NIGHT_BRIGHTNESS_PERCENT 25
#define SCREENSAVER_BRIGHTNESS_PERCENT 9
#define SCREENSAVER_FRAME_MS 50
#define BALL_SURFACE_SIZE 160
#define BALL_SURFACE_X 80
#define BALL_SURFACE_Y 40
#define DEFAULT_ROLE_COLOR 0xFF4FA3
#define AUDIO_RENDER_FPS_CAP 20
#define AUDIO_IO_CHUNK_SIZE 2048
#define MICROPHONE_SAMPLE_RATE 16000
#define MICROPHONE_GAIN_DB 42.0f

static_assert(INPUT_POLL_MS >= 10, "Touch polling must remain bounded");
static_assert(IMU_SAMPLE_MS >= 20, "CoreS3 IMU polling must remain bounded");

static const char *TAG = "companion_hardware";
static SemaphoreHandle_t s_board_mutex;
static SemaphoreHandle_t s_audio_mutex;
static QueueHandle_t s_touch_event_queue;
static TaskHandle_t s_ui_task_handle;
static esp_timer_handle_t s_ui_wake_timer;
static portMUX_TYPE s_activity_lock = portMUX_INITIALIZER_UNLOCKED;
static portMUX_TYPE s_playback_lock = portMUX_INITIALIZER_UNLOCKED;
static portMUX_TYPE s_expression_lock = portMUX_INITIALIZER_UNLOCKED;
static portMUX_TYPE s_imu_lock = portMUX_INITIALIZER_UNLOCKED;

static lv_display_t *s_display;
static lv_indev_t *s_touch_indev;
static lv_obj_t *s_screen;
static lv_obj_t *s_ball_root;
static lv_obj_t *s_shadow;
static lv_obj_t *s_middle;
static lv_obj_t *s_body;
static lv_obj_t *s_highlight;
static lv_obj_t *s_left_eye;
static lv_obj_t *s_right_eye;
static lv_obj_t *s_left_blush;
static lv_obj_t *s_right_blush;
static lv_obj_t *s_particles[8];
static lv_obj_t *s_orbit;
static lv_obj_t *s_orbit_tip;
static lv_obj_t *s_sleep_small;
static lv_obj_t *s_sleep_large;
static lv_obj_t *s_static_image;
static lv_image_dsc_t s_static_image_dsc;
static uint8_t *s_static_image_data;

static esp_codec_dev_handle_t s_speaker_codec;
static esp_codec_dev_handle_t s_microphone_codec;
static bool s_microphone_open;
static sensor_handle_t s_imu_sensor;
static bool s_imu_supported;
static float s_acceleration_x;
static float s_acceleration_y;
static float s_acceleration_z;
static uint64_t s_acceleration_timestamp;
static uint64_t s_consumed_acceleration_timestamp;

static int64_t s_last_activity_us;
static companion_face_state_t s_face_state = COMPANION_FACE_IDLE;
static bool s_connected;
static bool s_screensaver;
static size_t s_screensaver_frame;
static int64_t s_next_screensaver_frame_us;
static int64_t s_face_started_us;
static int64_t s_next_face_frame_us;
static bool s_initialized;
static bool s_playback_stop_requested;
static int s_volume_percent = 50;
static bool s_night_mode;
static bool s_dynamic_surface_active;
static uint32_t s_frame_display_lock_wait_us;
static companion_expression_engine_t s_expression_engine;
static companion_expression_diagnostics_t s_expression_diagnostics = {};
static uint32_t s_role_color = DEFAULT_ROLE_COLOR;
static bool s_body_palette_initialized;
static bool s_body_geometry_initialized;
static companion_expression_body_style_t s_body_palette_style;
static uint32_t s_accent_palette_color = 0xFFFFFFFFU;
static uint32_t s_last_body_update_ms;
static bool s_collect_refresh_metrics;
static uint32_t s_refresh_flush_count;
static uint32_t s_refresh_flush_pixels;
static uint32_t s_refresh_flush_wait_us;
static int64_t s_refresh_flush_wait_started_us;
static uint32_t s_last_refresh_metrics_log_ms;
static uint32_t s_frames_in_window;
static uint32_t s_window_started_ms;
static uint32_t s_stable_since_ms;
static uint8_t s_over_budget_frames;
static bool s_audio_playback_active;
static float s_last_acceleration_sum;
static int64_t s_last_shake_us;
static int64_t s_next_input_poll_us;
static companion_expression_fps_mode_t s_expression_fps_mode = COMPANION_EXPRESSION_FPS_ADAPTIVE;
static uint8_t s_expression_min_fps = 30U;
static uint8_t s_expression_max_fps = 60U;

// M5Unified kept the AW88298 register at 0 dB and applied its 0..255 master
// volume as a squared PCM amplitude. The official CoreS3 BSP declares the
// analog route's fixed gain (15 dB PA gain corrected by the default 3.3/5 V
// ratio) as about +11.39 dB; the codec driver subtracts that gain before it
// writes register 0x0C. Add the same route gain to the legacy attenuation
// curve so the resulting AW88298 register and perceived loudness match the
// previous implementation. Volume zero remains the library's -96 dB mute.
static esp_codec_dev_vol_map_t s_legacy_volume_curve[] = {
    {0, -96.0f},
    {5, -40.65f},
    {10, -28.61f},
    {20, -16.57f},
    {30, -9.53f},
    {40, -4.53f},
    {50, -0.65f},
    {60, 2.52f},
    {70, 5.19f},
    {80, 7.51f},
    {90, 9.56f},
    {100, 11.39f},
};

static void ui_wake_timer_callback(void *argument)
{
    (void)argument;
    TaskHandle_t task = s_ui_task_handle;
    if (task != nullptr) xTaskNotifyGive(task);
}

static void wait_for_next_ui_deadline(void)
{
    int64_t deadline_us = s_next_input_poll_us;
    if (s_screensaver) {
        if (deadline_us <= 0 || (s_next_screensaver_frame_us > 0 &&
            s_next_screensaver_frame_us < deadline_us)) {
            deadline_us = s_next_screensaver_frame_us;
        }
    } else if (!expression_pack_is_active() &&
               (deadline_us <= 0 || (s_next_face_frame_us > 0 &&
                s_next_face_frame_us < deadline_us))) {
        deadline_us = s_next_face_frame_us;
    }

    int64_t remaining_us = deadline_us - esp_timer_get_time();
    if (remaining_us <= UI_TIMER_MIN_WAIT_US || s_ui_wake_timer == nullptr) {
        taskYIELD();
        return;
    }

    (void)ulTaskNotifyTake(pdTRUE, 0);
    if (esp_timer_start_once(s_ui_wake_timer, (uint64_t)remaining_us) == ESP_OK) {
        (void)ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
    } else {
        taskYIELD();
    }
}

static uint32_t elapsed_us(int64_t started_us, int64_t finished_us)
{
    if (finished_us <= started_us) return 0U;
    uint64_t elapsed = (uint64_t)(finished_us - started_us);
    return elapsed > UINT32_MAX ? UINT32_MAX : (uint32_t)elapsed;
}

static void display_refresh_event_callback(lv_event_t *event)
{
    if (!s_collect_refresh_metrics) return;
    lv_event_code_t code = lv_event_get_code(event);
    if (code == LV_EVENT_FLUSH_START) {
        const lv_area_t *area = static_cast<const lv_area_t *>(lv_event_get_param(event));
        if (area != nullptr) {
            s_refresh_flush_count++;
            s_refresh_flush_pixels += (uint32_t)lv_area_get_size(area);
        }
    } else if (code == LV_EVENT_FLUSH_WAIT_START) {
        s_refresh_flush_wait_started_us = esp_timer_get_time();
    } else if (code == LV_EVENT_FLUSH_WAIT_FINISH && s_refresh_flush_wait_started_us > 0) {
        s_refresh_flush_wait_us += elapsed_us(s_refresh_flush_wait_started_us,
                                              esp_timer_get_time());
        s_refresh_flush_wait_started_us = 0;
    }
}

static bool supported_expression_fps(uint8_t fps)
{
    return fps >= 1U && fps <= 60U;
}

static uint8_t lower_expression_fps(uint8_t fps)
{
    uint8_t lower = fps > 5U ? (uint8_t)(fps - 5U) : 1U;
    return lower < s_expression_min_fps ? s_expression_min_fps : lower;
}

static uint8_t higher_expression_fps(uint8_t fps)
{
    uint8_t higher = fps <= 55U ? (uint8_t)(fps + 5U) : 60U;
    return higher > s_expression_max_fps ? s_expression_max_fps : higher;
}

static uint32_t lighten(uint32_t rgb, uint8_t percent)
{
    uint32_t red = (rgb >> 16) & 0xffU;
    uint32_t green = (rgb >> 8) & 0xffU;
    uint32_t blue = rgb & 0xffU;
    red += (255U - red) * percent / 100U;
    green += (255U - green) * percent / 100U;
    blue += (255U - blue) * percent / 100U;
    return (red << 16) | (green << 8) | blue;
}

static uint32_t blend(uint32_t from, uint32_t to, uint8_t percent)
{
    uint32_t inverse = 100U - percent;
    uint32_t red = (((from >> 16) & 0xffU) * inverse + ((to >> 16) & 0xffU) * percent) / 100U;
    uint32_t green = (((from >> 8) & 0xffU) * inverse + ((to >> 8) & 0xffU) * percent) / 100U;
    uint32_t blue = ((from & 0xffU) * inverse + (to & 0xffU) * percent) / 100U;
    return (red << 16) | (green << 8) | blue;
}

static bool take_mutex(SemaphoreHandle_t mutex, TickType_t timeout)
{
    return mutex != nullptr && xSemaphoreTake(mutex, timeout) == pdTRUE;
}

static bool audio_playback_active(void)
{
    bool active;
    taskENTER_CRITICAL(&s_playback_lock);
    active = s_audio_playback_active;
    taskEXIT_CRITICAL(&s_playback_lock);
    return active;
}

static void set_audio_playback_active(bool active)
{
    taskENTER_CRITICAL(&s_playback_lock);
    s_audio_playback_active = active;
    taskEXIT_CRITICAL(&s_playback_lock);
}

static int active_brightness_percent(void)
{
    return s_night_mode ? NIGHT_BRIGHTNESS_PERCENT : NORMAL_BRIGHTNESS_PERCENT;
}

static void restore_expression_fps_after_screensaver(void)
{
    taskENTER_CRITICAL(&s_expression_lock);
    s_expression_diagnostics.target_fps = s_expression_max_fps;
    s_expression_diagnostics.degrade_reason = COMPANION_EXPRESSION_DEGRADE_NONE;
    taskEXIT_CRITICAL(&s_expression_lock);
}

static void emit_touch_event(companion_touch_event_type_t type, int64_t occurred_us)
{
    if (s_touch_event_queue == nullptr) return;
    companion_touch_event_t event = {.type = type, .occurred_us = occurred_us};
    if (xQueueSend(s_touch_event_queue, &event, 0) != pdTRUE) {
        ESP_LOGW(TAG, "Touch event queue full; edge dropped safely");
    }
}

static void clear_playback_stop_request(void)
{
    taskENTER_CRITICAL(&s_playback_lock);
    s_playback_stop_requested = false;
    taskEXIT_CRITICAL(&s_playback_lock);
}

static bool playback_stop_requested(void)
{
    bool requested;
    taskENTER_CRITICAL(&s_playback_lock);
    requested = s_playback_stop_requested;
    taskEXIT_CRITICAL(&s_playback_lock);
    return requested;
}

static void set_hidden(lv_obj_t *object, bool hidden)
{
    if (lv_obj_has_flag(object, LV_OBJ_FLAG_HIDDEN) == hidden) return;
    if (hidden) lv_obj_add_flag(object, LV_OBJ_FLAG_HIDDEN);
    else lv_obj_remove_flag(object, LV_OBJ_FLAG_HIDDEN);
}

static lv_obj_t *create_shape(lv_obj_t *parent)
{
    lv_obj_t *object = lv_obj_create(parent);
    lv_obj_remove_style_all(object);
    lv_obj_set_style_bg_opa(object, LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(object, 0, 0);
    lv_obj_set_style_radius(object, LV_RADIUS_CIRCLE, 0);
    lv_obj_remove_flag(object, LV_OBJ_FLAG_SCROLLABLE);
    return object;
}

static void set_circle_geometry(lv_obj_t *object, int center_x, int center_y,
                                int diameter, float scale_x, float scale_y)
{
    // A transformed circle makes LVGL allocate and blend a transformed layer for every
    // large body object. Direct ellipse geometry produces the same silhouette while
    // keeping the hot path in the normal rounded-rectangle renderer.
    int width = (int)lroundf((float)diameter * scale_x);
    int height = (int)lroundf((float)diameter * scale_y);
    if (width < 3) width = 3;
    if (height < 3) height = 3;
    lv_obj_set_size(object, width, height);
    lv_obj_set_pos(object, center_x - width / 2, center_y - height / 2);
}

static int32_t capsule_rotation(float angle)
{
    float degrees = (1.5707963268f - angle) * 57.2957795f;
    while (degrees > 90.0f) degrees -= 180.0f;
    while (degrees < -90.0f) degrees += 180.0f;
    return (int32_t)lroundf(degrees * 10.0f);
}

static void set_capsule_geometry(lv_obj_t *object, int center_x, int center_y,
                                 float length, float thickness, float angle)
{
    int width = (int)lroundf(length < 3.0f ? 3.0f : length);
    int height = (int)lroundf(thickness < 3.0f ? 3.0f : thickness);
    if (width < height) width = height;
    lv_obj_set_size(object, width, height);
    lv_obj_set_pos(object, center_x - width / 2, center_y - height / 2);
    lv_obj_set_style_transform_pivot_x(object, width / 2, 0);
    lv_obj_set_style_transform_pivot_y(object, height / 2, 0);
    lv_obj_set_style_transform_rotation(object, capsule_rotation(angle), 0);
}

static void expression_body_palette(companion_expression_body_style_t style,
                                    uint32_t *body, uint32_t *shadow, uint32_t *shine)
{
    switch (style) {
        case COMPANION_BODY_BLUSH:
            *body = 0xF5C7D0; *shadow = 0xDCA4AE; *shine = 0xFFE7EB; break;
        case COMPANION_BODY_CORAL:
            *body = 0xFF5C4D; *shadow = 0xC93D37; *shine = 0xFF8B7E; break;
        case COMPANION_BODY_MUTED:
            *body = 0xA9AFB5; *shadow = 0x747B83; *shine = 0xD4D8DC; break;
        case COMPANION_BODY_UPDATE:
            *body = 0x9B8CF4; *shadow = 0x6857BE; *shine = 0xC9C0FF; break;
        case COMPANION_BODY_CREAM:
        default:
            *body = 0xF3EADF; *shadow = 0xCFC3B5; *shine = 0xFFF9F1; break;
    }
}

static void create_expression_scene_locked(void)
{
    s_screen = lv_screen_active();
    lv_obj_remove_style_all(s_screen);
    lv_obj_set_style_bg_color(s_screen, lv_color_hex(0x000000), 0);
    lv_obj_set_style_bg_opa(s_screen, LV_OPA_COVER, 0);
    s_ball_root = lv_obj_create(s_screen);
    lv_obj_remove_style_all(s_ball_root);
    lv_obj_set_pos(s_ball_root, BALL_SURFACE_X, BALL_SURFACE_Y);
    lv_obj_set_size(s_ball_root, BALL_SURFACE_SIZE, BALL_SURFACE_SIZE);
    lv_obj_set_style_bg_opa(s_ball_root, LV_OPA_TRANSP, 0);
    lv_obj_remove_flag(s_ball_root, LV_OBJ_FLAG_SCROLLABLE);
    // Keep every animated child clipped to the promised 160x160 transfer surface.
    lv_obj_remove_flag(s_ball_root, LV_OBJ_FLAG_OVERFLOW_VISIBLE);

    s_shadow = create_shape(s_ball_root);
    s_middle = create_shape(s_ball_root);
    s_body = create_shape(s_ball_root);
    s_highlight = create_shape(s_ball_root);
    s_left_blush = create_shape(s_ball_root);
    s_right_blush = create_shape(s_ball_root);
    lv_obj_set_style_bg_color(s_left_blush, lv_color_hex(0xE9829A), 0);
    lv_obj_set_style_bg_color(s_right_blush, lv_color_hex(0xE9829A), 0);
    s_orbit = lv_arc_create(s_ball_root);
    lv_obj_remove_style_all(s_orbit);
    lv_obj_set_style_arc_opa(s_orbit, LV_OPA_TRANSP, LV_PART_MAIN);
    lv_obj_set_style_arc_rounded(s_orbit, true, LV_PART_INDICATOR);
    lv_obj_set_style_bg_opa(s_orbit, LV_OPA_TRANSP, LV_PART_KNOB);
    lv_obj_remove_flag(s_orbit, LV_OBJ_FLAG_CLICKABLE);
    s_orbit_tip = create_shape(s_ball_root);
    for (lv_obj_t *&particle : s_particles) particle = create_shape(s_ball_root);
    s_left_eye = create_shape(s_ball_root);
    s_right_eye = create_shape(s_ball_root);
    lv_obj_set_style_bg_color(s_left_eye, lv_color_hex(0x111111), 0);
    lv_obj_set_style_bg_color(s_right_eye, lv_color_hex(0x111111), 0);
    s_sleep_small = lv_label_create(s_ball_root);
    s_sleep_large = lv_label_create(s_ball_root);
    lv_label_set_text_static(s_sleep_small, "z");
    lv_label_set_text_static(s_sleep_large, "Z");
    lv_obj_set_style_text_color(s_sleep_small, lv_color_hex(0xFFFFFF), 0);
    lv_obj_set_style_text_color(s_sleep_large, lv_color_hex(0xFFFFFF), 0);
    s_static_image = lv_image_create(s_screen);
    lv_obj_set_pos(s_static_image, 0, 0);

    set_hidden(s_static_image, true);
    set_hidden(s_left_blush, true);
    set_hidden(s_right_blush, true);
    set_hidden(s_orbit, true);
    set_hidden(s_orbit_tip, true);
    set_hidden(s_sleep_small, true);
    set_hidden(s_sleep_large, true);
    for (lv_obj_t *particle : s_particles) set_hidden(particle, true);
}

static void clear_static_image_locked(void)
{
    if (s_static_image_data == nullptr) return;
    set_hidden(s_static_image, true);
    lv_refr_now(s_display);
    lv_image_cache_drop(&s_static_image_dsc);
    free(s_static_image_data);
    s_static_image_data = nullptr;
    memset(&s_static_image_dsc, 0, sizeof(s_static_image_dsc));
}

static bool show_static_image_locked(uint8_t *image, size_t image_size)
{
    clear_static_image_locked();
    memset(&s_static_image_dsc, 0, sizeof(s_static_image_dsc));
    s_static_image_dsc.header.magic = LV_IMAGE_HEADER_MAGIC;
    s_static_image_dsc.header.cf = LV_COLOR_FORMAT_RAW;
    s_static_image_dsc.header.w = BSP_LCD_H_RES;
    s_static_image_dsc.header.h = BSP_LCD_V_RES;
    s_static_image_dsc.data_size = image_size > UINT32_MAX ? UINT32_MAX : (uint32_t)image_size;
    s_static_image_dsc.data = image;
    lv_image_header_t decoded_header = {};
    if (lv_image_decoder_get_info(&s_static_image_dsc, &decoded_header) != LV_RESULT_OK ||
        decoded_header.w != BSP_LCD_H_RES || decoded_header.h != BSP_LCD_V_RES) {
        memset(&s_static_image_dsc, 0, sizeof(s_static_image_dsc));
        return false;
    }
    s_static_image_data = image;
    lv_image_set_src(s_static_image, &s_static_image_dsc);
    set_hidden(s_ball_root, true);
    set_hidden(s_static_image, false);
    lv_obj_move_to_index(s_static_image, -1);
    lv_refr_now(s_display);
    return true;
}

static void update_dynamic_scene_locked(const companion_expression_pose_t &pose, uint32_t now_ms)
{
    uint32_t color = 0, shadow = 0, highlight = 0;
    expression_body_palette(pose.body_style, &color, &shadow, &highlight);
    uint32_t accent = s_role_color;
    int center_x = BALL_SURFACE_SIZE / 2 + (int)lroundf(pose.offset_x * 34.0f);
    int center_y = BALL_SURFACE_SIZE / 2 + (int)lroundf(pose.offset_y * 34.0f);

    set_hidden(s_static_image, true);
    set_hidden(s_ball_root, false);
    bool body_style_changed = !s_body_palette_initialized ||
                              s_body_palette_style != pose.body_style;
    bool update_body = !s_body_geometry_initialized || body_style_changed ||
                       (uint32_t)(now_ms - s_last_body_update_ms) >= 33U;
    // The four overlapping body ellipses dominate invalidated area. Keep their
    // breathing/motion layer at 30 Hz while eyes and foreground effects remain
    // eligible for 60 Hz, matching the proven legacy base/overlay cadence.
    if (update_body) {
        set_circle_geometry(s_shadow, center_x, center_y + 4, 136, pose.scale_x, pose.scale_y);
        set_circle_geometry(s_middle, center_x, center_y + 1, 132, pose.scale_x, pose.scale_y);
        set_circle_geometry(s_body, center_x, center_y - 2, 128, pose.scale_x, pose.scale_y);
        set_circle_geometry(s_highlight, center_x - 2, center_y - 5, 118, pose.scale_x, pose.scale_y);
        s_body_geometry_initialized = true;
        s_last_body_update_ms = now_ms;
    }
    if (body_style_changed) {
        lv_obj_set_style_bg_color(s_shadow, lv_color_hex(shadow), 0);
        lv_obj_set_style_bg_color(s_middle, lv_color_hex(blend(shadow, color, 62)), 0);
        lv_obj_set_style_bg_color(s_body, lv_color_hex(color), 0);
        lv_obj_set_style_bg_color(s_highlight, lv_color_hex(blend(color, highlight, 14)), 0);
        s_body_palette_style = pose.body_style;
        s_body_palette_initialized = true;
    }
    if (s_accent_palette_color != accent) {
        for (uint8_t index = 0; index < 8; index++) {
            lv_obj_set_style_bg_color(
                s_particles[index],
                lv_color_hex(index % 2 == 0 ? accent : lighten(accent, 42)), 0);
        }
        lv_obj_set_style_arc_color(s_orbit, lv_color_hex(accent), LV_PART_INDICATOR);
        lv_obj_set_style_bg_color(s_orbit_tip, lv_color_hex(lighten(accent, 36)), 0);
        s_accent_palette_color = accent;
    }

    float eye_open = pose.eye_open < 0.0f ? 0.0f : (pose.eye_open > 1.0f ? 1.0f : pose.eye_open);
    int eye_spacing = (int)lroundf(pose.eye_spacing * 74.0f);
    int eye_y = center_y - 4 + (int)lroundf(pose.gaze_y * 20.0f);
    int gaze_x = (int)lroundf(pose.gaze_x * 14.0f);
    float left_length = 28.0f + (pose.left_eye_length * 72.0f - 28.0f) * eye_open;
    float right_length = 28.0f + (pose.right_eye_length * 72.0f - 28.0f) * eye_open;
    float left_thickness = 3.0f + (pose.left_eye_thickness * 60.0f - 3.0f) * eye_open;
    float right_thickness = 3.0f + (pose.right_eye_thickness * 60.0f - 3.0f) * eye_open;
    float left_angle = 1.5708f + (pose.left_eye_angle - 1.5708f) * eye_open;
    float right_angle = -1.5708f + (pose.right_eye_angle + 1.5708f) * eye_open;
    set_capsule_geometry(s_left_eye, center_x - eye_spacing + gaze_x, eye_y,
                         left_length, left_thickness, left_angle);
    set_capsule_geometry(s_right_eye, center_x + eye_spacing + gaze_x, eye_y,
                         right_length, right_thickness, right_angle);

    bool show_blush = pose.blush > 0.05f;
    set_hidden(s_left_blush, !show_blush);
    set_hidden(s_right_blush, !show_blush);
    if (show_blush) {
        int width = 12 + (int)lroundf(pose.blush * 8.0f);
        int height = 5 + (int)lroundf(pose.blush * 5.0f);
        lv_obj_set_size(s_left_blush, width, height);
        lv_obj_set_size(s_right_blush, width, height);
        lv_obj_set_pos(s_left_blush, center_x - 44 - width / 2, center_y + 24 - height / 2);
        lv_obj_set_pos(s_right_blush, center_x + 44 - width / 2, center_y + 24 - height / 2);
        lv_opa_t opacity = (lv_opa_t)lroundf(120.0f + pose.blush * 100.0f);
        lv_obj_set_style_bg_opa(s_left_blush, opacity, 0);
        lv_obj_set_style_bg_opa(s_right_blush, opacity, 0);
    }

    static const int8_t positions[8][2] = {
        {-68, -48}, {-51, -68}, {51, -66}, {69, -38},
        {-72, 20}, {70, 24}, {-48, 66}, {49, 68},
    };
    for (uint8_t index = 0; index < 8; index++) {
        bool visible = index < pose.particle_count;
        set_hidden(s_particles[index], !visible);
        if (!visible) continue;
        int diameter = index % 3 == 0 ? 6 : 4;
        int wobble = (int)lroundf(sinf((float)now_ms / 180.0f + index) * 3.0f);
        lv_obj_set_size(s_particles[index], diameter, diameter);
        lv_obj_set_pos(s_particles[index], center_x + positions[index][0] - diameter / 2,
                       center_y + positions[index][1] + wobble - diameter / 2);
    }

    bool show_orbit = pose.orbit > 0.05f;
    set_hidden(s_orbit, !show_orbit);
    set_hidden(s_orbit_tip, !show_orbit);
    if (show_orbit) {
        int phase = (int)((now_ms / 12U) % 360U);
        int radius = 74;
        int width = 2 + (int)lroundf(pose.orbit * 2.0f);
        lv_obj_set_size(s_orbit, radius * 2, radius * 2);
        lv_obj_set_pos(s_orbit, center_x - radius, center_y - radius);
        lv_arc_set_bg_angles(s_orbit, 0, 360);
        lv_arc_set_angles(s_orbit, phase, phase + 62);
        lv_obj_set_style_arc_width(s_orbit, width, LV_PART_INDICATOR);
        float angle = (float)(phase + 62) * 0.0174532925f;
        int diameter = 4 + width;
        lv_obj_set_size(s_orbit_tip, diameter, diameter);
        lv_obj_set_pos(s_orbit_tip,
                       center_x + (int)lroundf(cosf(angle) * radius) - diameter / 2,
                       center_y + (int)lroundf(sinf(angle) * radius) - diameter / 2);
    }

    set_hidden(s_sleep_small, !pose.sleeping);
    set_hidden(s_sleep_large, !pose.sleeping);
    if (pose.sleeping) {
        lv_obj_set_pos(s_sleep_small, center_x + 52, center_y - 60);
        lv_obj_set_pos(s_sleep_large, center_x + 61, center_y - 73);
    }
}

static void draw_builtin_face_locked(companion_face_state_t state)
{
    int64_t started_us = esp_timer_get_time();
    uint32_t now_ms = (uint32_t)(started_us / 1000LL);
    companion_expression_engine_set_system(&s_expression_engine, state, now_ms);
    companion_expression_pose_t pose = {};
    companion_expression_engine_tick(&s_expression_engine, now_ms, &pose);
    taskENTER_CRITICAL(&s_expression_lock);
    uint8_t fps = s_expression_diagnostics.target_fps == 0 ? 20 : s_expression_diagnostics.target_fps;
    taskEXIT_CRITICAL(&s_expression_lock);
    bool playback_active = audio_playback_active();
    uint8_t render_fps = playback_active && fps > AUDIO_RENDER_FPS_CAP ? AUDIO_RENDER_FPS_CAP : fps;

    int64_t display_lock_started_us = esp_timer_get_time();
    if (!bsp_display_lock(1000)) {
        s_frame_display_lock_wait_us = elapsed_us(display_lock_started_us, esp_timer_get_time());
        return;
    }
    s_frame_display_lock_wait_us = elapsed_us(display_lock_started_us, esp_timer_get_time());
    clear_static_image_locked();

    update_dynamic_scene_locked(pose, now_ms);
    s_refresh_flush_count = 0;
    s_refresh_flush_pixels = 0;
    s_refresh_flush_wait_us = 0;
    s_refresh_flush_wait_started_us = 0;
    s_collect_refresh_metrics = true;
    int64_t refresh_started_us = esp_timer_get_time();
    lv_refr_now(s_display);
    int64_t finished_us = esp_timer_get_time();
    s_collect_refresh_metrics = false;
    bsp_display_unlock();

    if ((uint32_t)(now_ms - s_last_refresh_metrics_log_ms) >= 5000U) {
        ESP_LOGI(TAG,
                 "Expression refresh: total=%" PRIu32 " us flushes=%" PRIu32
                 " pixels=%" PRIu32 " wait=%" PRIu32 " us",
                 elapsed_us(refresh_started_us, finished_us),
                 s_refresh_flush_count, s_refresh_flush_pixels,
                 s_refresh_flush_wait_us);
        s_last_refresh_metrics_log_ms = now_ms;
    }

    taskENTER_CRITICAL(&s_expression_lock);
    s_expression_diagnostics.draw_time_us = elapsed_us(started_us, refresh_started_us);
    s_expression_diagnostics.transfer_time_us = elapsed_us(refresh_started_us, finished_us);
    s_expression_diagnostics.active_layer = companion_expression_engine_active_layer(
        &s_expression_engine, now_ms);
    s_expression_diagnostics.minimum_free_heap = heap_caps_get_minimum_free_size(MALLOC_CAP_8BIT);
    taskEXIT_CRITICAL(&s_expression_lock);
    s_dynamic_surface_active = true;
    s_screensaver_frame = 0;
    int64_t interval_us = 1000000LL / render_fps;
    s_next_face_frame_us = started_us + interval_us;
    if (s_next_face_frame_us <= finished_us) s_next_face_frame_us = finished_us + 1000LL;
}

static void draw_face_locked(companion_face_state_t state)
{
    uint8_t *image = nullptr;
    size_t image_size = 0;
    if (expression_pack_read_state(state, &image, &image_size) == ESP_OK) {
        int64_t lock_started_us = esp_timer_get_time();
        if (bsp_display_lock(1000)) {
            s_frame_display_lock_wait_us = elapsed_us(lock_started_us, esp_timer_get_time());
            bool rendered = show_static_image_locked(image, image_size);
            bsp_display_unlock();
            if (rendered) {
                s_dynamic_surface_active = false;
                s_screensaver_frame = 0;
                s_next_face_frame_us = esp_timer_get_time() + 50000LL;
                return;
            }
        }
        free(image);
    }
    draw_builtin_face_locked(state);
}

static companion_face_state_t visible_state_locked(void)
{
    return companion_interaction_visible_state(s_face_state, s_connected);
}

static void draw_screensaver_locked(void)
{
    uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000LL);
    if (companion_expression_engine_active_layer(&s_expression_engine, now_ms) !=
        COMPANION_EXPRESSION_LAYER_PHYSICAL) {
        companion_expression_engine_trigger(&s_expression_engine,
                                            COMPANION_BEHAVIOR_DROWSY_SLEEP,
                                            3000U,
                                            now_ms);
    }
    draw_builtin_face_locked(visible_state_locked());
}

static void update_expression_performance(int64_t now_us, int64_t frame_deadline_us,
                                          uint32_t lock_wait_us, bool rendered)
{
    uint32_t now_ms = (uint32_t)(now_us / 1000LL);
    bool busy = audio_playback_active();
    taskENTER_CRITICAL(&s_expression_lock);
    lock_wait_us += s_frame_display_lock_wait_us;
    s_frame_display_lock_wait_us = 0U;
    s_expression_diagnostics.display_lock_wait_us = lock_wait_us;
    uint8_t target = s_expression_diagnostics.target_fps;
    uint32_t budget_us = target == 0 ? 50000U : 1000000U / target;
    uint32_t frame_time_us = s_expression_diagnostics.draw_time_us +
                             s_expression_diagnostics.transfer_time_us;
    bool draw_over = rendered && frame_time_us > budget_us * 9U / 10U;
    bool lock_over = lock_wait_us > budget_us / 2U;
    if (rendered) {
        s_frames_in_window++;
        if (frame_deadline_us > 0 && now_us > frame_deadline_us + (int64_t)budget_us) {
            s_expression_diagnostics.dropped_frames++;
        }
    }
    if (s_window_started_ms == 0) s_window_started_ms = now_ms;
    uint32_t window_elapsed_ms = now_ms - s_window_started_ms;
    if (window_elapsed_ms >= 1000U) {
        uint32_t measured_fps = window_elapsed_ms == 0U ? 0U :
            (s_frames_in_window * 1000U + window_elapsed_ms / 2U) / window_elapsed_ms;
        s_expression_diagnostics.actual_fps = measured_fps > 255U ?
                                              255U : (uint8_t)measured_fps;
        s_frames_in_window = 0;
        s_window_started_ms = now_ms;
    }
    if (s_screensaver) {
        s_expression_diagnostics.target_fps = 20U;
        s_expression_diagnostics.degrade_reason = COMPANION_EXPRESSION_DEGRADE_IDLE_SLEEP;
        s_stable_since_ms = now_ms;
    } else if (s_expression_fps_mode == COMPANION_EXPRESSION_FPS_FIXED) {
        s_expression_diagnostics.target_fps = s_expression_max_fps;
        s_expression_diagnostics.degrade_reason = busy ?
            COMPANION_EXPRESSION_DEGRADE_AUDIO_BUSY : COMPANION_EXPRESSION_DEGRADE_NONE;
        s_over_budget_frames = 0;
    } else if (!rendered) {
        // Adapt only from completed frames. Reusing the previous frame timing on every
        // 2 ms UI-loop pass made the controller race through several FPS levels before
        // another frame had even been measured.
    } else if (busy && target > s_expression_min_fps) {
        s_expression_diagnostics.target_fps = lower_expression_fps(target);
        s_expression_diagnostics.degrade_reason = COMPANION_EXPRESSION_DEGRADE_AUDIO_BUSY;
        s_stable_since_ms = now_ms;
    } else if (draw_over || lock_over) {
        if (++s_over_budget_frames >= 4U) {
            s_expression_diagnostics.target_fps = lower_expression_fps(target);
            s_expression_diagnostics.degrade_reason = draw_over ?
                COMPANION_EXPRESSION_DEGRADE_DRAW_BUDGET :
                COMPANION_EXPRESSION_DEGRADE_DISPLAY_LOCK;
            s_over_budget_frames = 0;
            s_stable_since_ms = now_ms;
        }
    } else {
        s_over_budget_frames = 0;
        if (s_stable_since_ms == 0) s_stable_since_ms = now_ms;
        if (!busy && now_ms - s_stable_since_ms >= 10000U && target < s_expression_max_fps) {
            s_expression_diagnostics.target_fps = higher_expression_fps(target);
            s_expression_diagnostics.degrade_reason = COMPANION_EXPRESSION_DEGRADE_NONE;
            s_stable_since_ms = now_ms;
        }
    }
    taskEXIT_CRITICAL(&s_expression_lock);
}

static void sensor_event_handler(void *handler_args, esp_event_base_t base,
                                 int32_t id, void *event_data)
{
    (void)handler_args;
    (void)base;
    if (id != SENSOR_ACCE_DATA_READY || event_data == nullptr) return;
    const sensor_data_t *data = static_cast<const sensor_data_t *>(event_data);
    taskENTER_CRITICAL(&s_imu_lock);
    s_acceleration_x = data->acce.x;
    s_acceleration_y = data->acce.y;
    s_acceleration_z = data->acce.z;
    s_acceleration_timestamp = data->timestamp;
    taskEXIT_CRITICAL(&s_imu_lock);
}

static void poll_shake_sensor(int64_t now_us)
{
    if (!s_imu_supported || now_us - s_last_shake_us < 2000000LL) return;
    float x, y, z;
    uint64_t timestamp;
    taskENTER_CRITICAL(&s_imu_lock);
    x = s_acceleration_x;
    y = s_acceleration_y;
    z = s_acceleration_z;
    timestamp = s_acceleration_timestamp;
    taskEXIT_CRITICAL(&s_imu_lock);
    if (timestamp == 0 || timestamp == s_consumed_acceleration_timestamp) return;
    s_consumed_acceleration_timestamp = timestamp;
    float sum = fabsf(x) + fabsf(y) + fabsf(z);
    if (s_last_acceleration_sum > 0.0f && fabsf(sum - s_last_acceleration_sum) > 1.35f) {
        companion_expression_engine_trigger(&s_expression_engine,
                                            COMPANION_BEHAVIOR_SHAKE_DIZZY,
                                            2600U,
                                            (uint32_t)(now_us / 1000LL));
        s_last_shake_us = now_us;
    }
    s_last_acceleration_sum = sum;
}

static void set_last_activity_now(void)
{
    taskENTER_CRITICAL(&s_activity_lock);
    s_last_activity_us = esp_timer_get_time();
    taskEXIT_CRITICAL(&s_activity_lock);
}

static int64_t last_activity_us(void)
{
    int64_t value;
    taskENTER_CRITICAL(&s_activity_lock);
    value = s_last_activity_us;
    taskEXIT_CRITICAL(&s_activity_lock);
    return value;
}

static void set_display_brightness(int percent)
{
    esp_err_t err = bsp_display_brightness_set(percent);
    if (err != ESP_OK) ESP_LOGW(TAG, "Display brightness update failed: %s", esp_err_to_name(err));
}

extern "C" void companion_hardware_mark_activity(void)
{
    set_last_activity_now();
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return;
    if (s_screensaver) {
        s_screensaver = false;
        restore_expression_fps_after_screensaver();
        companion_expression_engine_trigger(&s_expression_engine, COMPANION_BEHAVIOR_WAKE,
                                            1800U, (uint32_t)(esp_timer_get_time() / 1000LL));
        set_display_brightness(active_brightness_percent());
        if (expression_pack_is_active()) draw_face_locked(visible_state_locked());
        else s_next_face_frame_us = esp_timer_get_time();
    }
    xSemaphoreGive(s_board_mutex);
}

extern "C" void companion_hardware_set_state(companion_face_state_t state)
{
    set_last_activity_now();
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) return;
    s_face_state = state;
    s_face_started_us = esp_timer_get_time();
    bool was_screensaver = s_screensaver;
    s_screensaver = false;
    if (was_screensaver) restore_expression_fps_after_screensaver();
    set_display_brightness(active_brightness_percent());
    companion_expression_engine_set_system(&s_expression_engine, visible_state_locked(),
                                           (uint32_t)(s_face_started_us / 1000LL));
    if (expression_pack_is_active()) draw_face_locked(visible_state_locked());
    else s_next_face_frame_us = s_face_started_us;
    xSemaphoreGive(s_board_mutex);
}

extern "C" void companion_hardware_refresh_face(void)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) return;
    s_face_started_us = esp_timer_get_time();
    bool was_screensaver = s_screensaver;
    s_screensaver = false;
    if (was_screensaver) restore_expression_fps_after_screensaver();
    set_display_brightness(active_brightness_percent());
    draw_face_locked(visible_state_locked());
    xSemaphoreGive(s_board_mutex);
}

extern "C" void companion_hardware_set_connected(bool connected)
{
    set_last_activity_now();
    if (!s_initialized) {
        s_connected = connected;
        return;
    }
    if (!take_mutex(s_board_mutex, portMAX_DELAY)) return;
    if (s_connected != connected || s_screensaver) {
        bool was_screensaver = s_screensaver;
        s_connected = connected;
        s_face_started_us = esp_timer_get_time();
        s_screensaver = false;
        if (was_screensaver) restore_expression_fps_after_screensaver();
        set_display_brightness(active_brightness_percent());
        companion_expression_engine_set_system(&s_expression_engine, visible_state_locked(),
                                               (uint32_t)(s_face_started_us / 1000LL));
        if (expression_pack_is_active()) draw_face_locked(visible_state_locked());
        else s_next_face_frame_us = s_face_started_us;
    }
    xSemaphoreGive(s_board_mutex);
}

static bool sample_touch(bool *touched, uint32_t *lock_wait_us)
{
    if (touched == nullptr || lock_wait_us == nullptr) return false;
    int64_t started_us = esp_timer_get_time();
    if (!bsp_display_lock(20)) {
        *lock_wait_us += elapsed_us(started_us, esp_timer_get_time());
        return false;
    }
    *lock_wait_us += elapsed_us(started_us, esp_timer_get_time());
    *touched = s_touch_indev != nullptr &&
               lv_indev_get_state(s_touch_indev) == LV_INDEV_STATE_PRESSED;
    bsp_display_unlock();
    return true;
}

static void ui_task(void *argument)
{
    (void)argument;
    vTaskDelay(pdMS_TO_TICKS(UI_STARTUP_GRACE_MS));
    bool previously_touched = false;
    bool current_touch = false;
    bool consume_current_touch = false;
    for (;;) {
        int64_t now = esp_timer_get_time();
        bool touched = current_touch;
        bool input_sampled = false;
        bool screensaver_was_active = false;
        uint32_t lock_wait_us = 0;
        int64_t lock_started_us = now;
        if (now >= s_next_input_poll_us && take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            lock_wait_us = elapsed_us(lock_started_us, esp_timer_get_time());
            input_sampled = sample_touch(&touched, &lock_wait_us);
            if (input_sampled) current_touch = touched;
            int64_t sensor_now = esp_timer_get_time();
            s_next_input_poll_us = sensor_now + (int64_t)INPUT_POLL_MS * 1000LL;
            poll_shake_sensor(sensor_now);
            screensaver_was_active = s_screensaver;
            xSemaphoreGive(s_board_mutex);
        }
        now = esp_timer_get_time();
        if (input_sampled && touched && !previously_touched) {
            consume_current_touch = screensaver_was_active;
            if (!consume_current_touch) {
                emit_touch_event(COMPANION_TOUCH_PRESSED, now);
                if (take_mutex(s_board_mutex, pdMS_TO_TICKS(20))) {
                    companion_expression_engine_suggest_emotion(
                        &s_expression_engine, COMPANION_EMOTION_LOVING,
                        COMPANION_EMOTION_INTENSITY_MEDIUM, 5000U,
                        (uint32_t)(now / 1000LL));
                    xSemaphoreGive(s_board_mutex);
                }
            }
        } else if (input_sampled && !touched && previously_touched) {
            if (!consume_current_touch) emit_touch_event(COMPANION_TOUCH_RELEASED, now);
            consume_current_touch = false;
        }
        if (input_sampled) previously_touched = touched;
        if (input_sampled && touched) companion_hardware_mark_activity();

        bool idle = now - last_activity_us() >=
                    (int64_t)CONFIG_STACKCHAN_SCREENSAVER_IDLE_SECONDS * 1000LL * 1000LL;
        bool rendered = false;
        int64_t frame_deadline_us = s_next_face_frame_us;
        lock_started_us = esp_timer_get_time();
        if (take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            lock_wait_us += elapsed_us(lock_started_us, esp_timer_get_time());
            companion_face_state_t visible = visible_state_locked();
            if (idle && companion_interaction_allows_screensaver(visible) && !s_screensaver) {
                s_face_started_us = now;
                if (!expression_pack_is_active()) {
                    companion_expression_engine_trigger(&s_expression_engine,
                                                        COMPANION_BEHAVIOR_DROWSY_SLEEP,
                                                        3000U,
                                                        (uint32_t)(now / 1000LL));
                    draw_builtin_face_locked(visible);
                    rendered = true;
                }
                s_screensaver = true;
                s_screensaver_frame = 1;
                s_next_screensaver_frame_us = now + (int64_t)SCREENSAVER_FRAME_MS * 1000LL;
                set_display_brightness(SCREENSAVER_BRIGHTNESS_PERCENT);
                ESP_LOGI(TAG, "Idle low-brightness expression screensaver active");
            } else if (s_screensaver && !companion_interaction_allows_screensaver(visible)) {
                s_screensaver = false;
                restore_expression_fps_after_screensaver();
                set_display_brightness(active_brightness_percent());
                draw_face_locked(visible);
                rendered = true;
            } else if (s_screensaver && now >= s_next_screensaver_frame_us) {
                if (!expression_pack_is_active()) {
                    draw_screensaver_locked();
                    rendered = true;
                }
                s_screensaver_frame++;
                s_next_screensaver_frame_us = now + 1000000LL / 20LL;
            } else if (!s_screensaver && !expression_pack_is_active() &&
                       now >= s_next_face_frame_us) {
                draw_face_locked(visible);
                rendered = true;
            }
            xSemaphoreGive(s_board_mutex);
        }
        update_expression_performance(esp_timer_get_time(), frame_deadline_us,
                                      lock_wait_us, rendered);
        wait_for_next_ui_deadline();
    }
}

static esp_err_t open_microphone(void)
{
    if (s_microphone_open) return ESP_OK;
    esp_codec_dev_sample_info_t format = {};
    format.sample_rate = MICROPHONE_SAMPLE_RATE;
    format.channel = 1;
    format.bits_per_sample = 16;
    format.mclk_multiple = I2S_MCLK_MULTIPLE_384;
    esp_err_t err = esp_codec_dev_open(s_microphone_codec, &format);
    if (err == ESP_OK) s_microphone_open = true;
    return err;
}

static esp_err_t close_microphone(void)
{
    if (!s_microphone_open) return ESP_OK;
    esp_err_t err = esp_codec_dev_close(s_microphone_codec);
    if (err == ESP_OK) s_microphone_open = false;
    return err;
}

extern "C" esp_err_t companion_hardware_init(void)
{
    if (s_initialized) return ESP_OK;
    s_board_mutex = xSemaphoreCreateMutex();
    s_audio_mutex = xSemaphoreCreateMutex();
    s_touch_event_queue = xQueueCreate(TOUCH_EVENT_QUEUE_LENGTH, sizeof(companion_touch_event_t));
    if (s_board_mutex == nullptr || s_audio_mutex == nullptr || s_touch_event_queue == nullptr) {
        return ESP_ERR_NO_MEM;
    }

    bsp_display_cfg_t display_config = {};
    display_config.lvgl_port_cfg = ESP_LVGL_PORT_INIT_CONFIG();
    display_config.lvgl_port_cfg.task_priority = 3;
    display_config.lvgl_port_cfg.task_stack = 8192;
    display_config.lvgl_port_cfg.task_affinity = UI_TASK_CORE;
    display_config.lvgl_port_cfg.task_max_sleep_ms = 16;
    display_config.lvgl_port_cfg.timer_period_ms = 5;
    display_config.buffer_size = BSP_LCD_H_RES * CONFIG_BSP_LCD_DRAW_BUF_HEIGHT;
    display_config.double_buffer = CONFIG_BSP_LCD_DRAW_BUF_DOUBLE;
    display_config.flags.buff_dma = true;
    display_config.flags.buff_spiram = false;
    display_config.flags.sw_rotate = false;
    s_display = bsp_display_start_with_config(&display_config);
    if (s_display == nullptr) return ESP_FAIL;
    lv_display_add_event_cb(s_display, display_refresh_event_callback,
                            LV_EVENT_FLUSH_START, nullptr);
    lv_display_add_event_cb(s_display, display_refresh_event_callback,
                            LV_EVENT_FLUSH_WAIT_START, nullptr);
    lv_display_add_event_cb(s_display, display_refresh_event_callback,
                            LV_EVENT_FLUSH_WAIT_FINISH, nullptr);
    s_touch_indev = bsp_display_get_input_dev();
    if (!bsp_display_lock(0)) return ESP_ERR_TIMEOUT;
    create_expression_scene_locked();
    bsp_display_unlock();
    set_display_brightness(active_brightness_percent());

    s_speaker_codec = bsp_audio_codec_speaker_init();
    s_microphone_codec = bsp_audio_codec_microphone_init();
    if (s_speaker_codec == nullptr || s_microphone_codec == nullptr) return ESP_FAIL;
    esp_codec_dev_vol_curve_t volume_curve = {
        .vol_map = s_legacy_volume_curve,
        .count = (int)(sizeof(s_legacy_volume_curve) / sizeof(s_legacy_volume_curve[0])),
    };
    esp_err_t err = esp_codec_dev_set_vol_curve(s_speaker_codec, &volume_curve);
    if (err != ESP_OK) return err;
    err = esp_codec_dev_set_out_vol(s_speaker_codec, s_volume_percent);
    if (err != ESP_OK) return err;
    err = esp_codec_dev_set_in_gain(s_microphone_codec, MICROPHONE_GAIN_DB);
    if (err != ESP_OK) return err;
    err = open_microphone();
    if (err != ESP_OK) return err;

    bsp_sensor_config_t imu_config = {
        .type = IMU_ID,
        .mode = MODE_POLLING,
        .period = IMU_SAMPLE_MS,
    };
    esp_err_t imu_err = bsp_sensor_init(&imu_config, &s_imu_sensor);
    if (imu_err == ESP_OK) {
        imu_err = iot_sensor_handler_register(s_imu_sensor, sensor_event_handler, nullptr);
    }
    if (imu_err == ESP_OK) imu_err = iot_sensor_start(s_imu_sensor);
    s_imu_supported = imu_err == ESP_OK;
    if (!s_imu_supported) ESP_LOGW(TAG, "BMI270 unavailable: %s", esp_err_to_name(imu_err));

    s_last_activity_us = esp_timer_get_time();
    s_face_started_us = s_last_activity_us;
    companion_expression_engine_init(&s_expression_engine,
                                     (uint32_t)(s_last_activity_us / 1000LL));
    s_expression_diagnostics.target_fps = s_expression_max_fps;
    s_expression_diagnostics.dynamic_renderer = true;
    s_expression_diagnostics.imu_supported = s_imu_supported;
    s_expression_diagnostics.proximity_supported = false;
    s_initialized = true;
    if (!take_mutex(s_board_mutex, portMAX_DELAY)) return ESP_ERR_TIMEOUT;
    draw_face_locked(visible_state_locked());
    xSemaphoreGive(s_board_mutex);

    esp_timer_create_args_t ui_timer_args = {};
    ui_timer_args.callback = ui_wake_timer_callback;
    ui_timer_args.dispatch_method = ESP_TIMER_TASK;
    ui_timer_args.name = "companion_ui_deadline";
    err = esp_timer_create(&ui_timer_args, &s_ui_wake_timer);
    if (err != ESP_OK) {
        s_initialized = false;
        return err;
    }
    if (xTaskCreatePinnedToCore(ui_task, "companion_ui", UI_TASK_STACK_SIZE, nullptr,
                                UI_TASK_PRIORITY, &s_ui_task_handle, UI_TASK_CORE) != pdPASS) {
        esp_timer_delete(s_ui_wake_timer);
        s_ui_wake_timer = nullptr;
        s_initialized = false;
        return ESP_ERR_NO_MEM;
    }
    ESP_LOGI(TAG,
             "Official CoreS3 BSP initialized: LVGL display/touch core=%d, codec audio and BMI270=%s",
             UI_TASK_CORE, s_imu_supported ? "ready" : "unavailable");
    return ESP_OK;
}

extern "C" esp_err_t companion_hardware_record_pcm(int16_t *samples,
                                                     size_t sample_count,
                                                     uint32_t sample_rate)
{
    if (!s_initialized || samples == nullptr || sample_count == 0 ||
        sample_rate != MICROPHONE_SAMPLE_RATE || sample_count > SIZE_MAX / sizeof(int16_t)) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_audio_mutex, portMAX_DELAY)) return ESP_ERR_TIMEOUT;
    esp_err_t err = open_microphone();
    if (err == ESP_OK) {
        err = esp_codec_dev_read(s_microphone_codec, samples, sample_count * sizeof(int16_t));
    }
    xSemaphoreGive(s_audio_mutex);
    return err;
}

extern "C" esp_err_t companion_hardware_play_wav(const uint8_t *wav, size_t wav_size)
{
    return companion_hardware_play_wav_interruptible(wav, wav_size, nullptr);
}

extern "C" esp_err_t companion_hardware_play_wav_interruptible(const uint8_t *wav,
                                                                 size_t wav_size,
                                                                 bool *cancelled)
{
    audio_wav_view_t view = {};
    if (!s_initialized || !audio_wav_parse(wav, wav_size, &view)) return ESP_ERR_INVALID_ARG;
    if (!take_mutex(s_audio_mutex, portMAX_DELAY)) return ESP_ERR_TIMEOUT;
    if (cancelled != nullptr) *cancelled = false;
    clear_playback_stop_request();
    set_audio_playback_active(true);

    esp_err_t err = close_microphone();
    esp_codec_dev_sample_info_t format = {};
    format.sample_rate = view.sample_rate;
    format.channel = view.channels;
    format.bits_per_sample = view.bits_per_sample;
    format.mclk_multiple = I2S_MCLK_MULTIPLE_384;
    if (err == ESP_OK) err = esp_codec_dev_set_out_vol(s_speaker_codec, s_volume_percent);
    if (err == ESP_OK) err = esp_codec_dev_open(s_speaker_codec, &format);
    bool speaker_open = err == ESP_OK;
    bool stopped = false;
    size_t offset = 0;
    while (err == ESP_OK && offset < view.data_size) {
        if (playback_stop_requested()) {
            stopped = true;
            break;
        }
        size_t remaining = view.data_size - offset;
        size_t chunk = remaining < AUDIO_IO_CHUNK_SIZE ? remaining : AUDIO_IO_CHUNK_SIZE;
        err = esp_codec_dev_write(s_speaker_codec, (void *)(view.data + offset), chunk);
        offset += chunk;
    }
    if (speaker_open) {
        esp_err_t close_err = esp_codec_dev_close(s_speaker_codec);
        if (err == ESP_OK) err = close_err;
    }
    esp_err_t microphone_err = open_microphone();
    set_audio_playback_active(false);
    xSemaphoreGive(s_audio_mutex);

    if (cancelled != nullptr) *cancelled = stopped;
    if (microphone_err != ESP_OK) {
        ESP_LOGE(TAG, "Microphone did not restart after playback: %s", esp_err_to_name(microphone_err));
        return ESP_ERR_INVALID_STATE;
    }
    if (stopped) return ESP_OK;
    if (err != ESP_OK) {
        taskENTER_CRITICAL(&s_expression_lock);
        s_expression_diagnostics.audio_underruns++;
        if (s_expression_fps_mode == COMPANION_EXPRESSION_FPS_ADAPTIVE) {
            s_expression_diagnostics.target_fps = s_expression_min_fps;
        }
        s_expression_diagnostics.degrade_reason = COMPANION_EXPRESSION_DEGRADE_AUDIO_UNDERRUN;
        taskEXIT_CRITICAL(&s_expression_lock);
    }
    return err;
}

extern "C" void companion_hardware_request_playback_stop(void)
{
    taskENTER_CRITICAL(&s_playback_lock);
    s_playback_stop_requested = true;
    taskEXIT_CRITICAL(&s_playback_lock);
}

extern "C" esp_err_t companion_hardware_configure_interaction(int volume_percent,
                                                               bool night_mode)
{
    if (!s_initialized || volume_percent < 0 || volume_percent > 100) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) return ESP_ERR_TIMEOUT;
    s_volume_percent = volume_percent;
    s_night_mode = night_mode;
    esp_err_t err = esp_codec_dev_set_out_vol(s_speaker_codec, s_volume_percent);
    ESP_LOGI(TAG, "Interaction audio configured: volume=%d%% night_mode=%s",
             s_volume_percent, s_night_mode ? "true" : "false");
    if (!s_screensaver) set_display_brightness(active_brightness_percent());
    xSemaphoreGive(s_board_mutex);
    return err;
}

extern "C" bool companion_hardware_wait_touch_event(companion_touch_event_t *event,
                                                       uint32_t timeout_ms)
{
    if (event == nullptr || s_touch_event_queue == nullptr) return false;
    TickType_t timeout = pdMS_TO_TICKS(timeout_ms);
    if (timeout_ms > 0 && timeout == 0) timeout = 1;
    return xQueueReceive(s_touch_event_queue, event, timeout) == pdTRUE;
}

extern "C" esp_err_t companion_hardware_configure_expression(
    uint32_t rgb, companion_emotion_t emotion,
    companion_emotion_intensity_t intensity, uint32_t duration_ms)
{
    if (!s_initialized || rgb > 0xFFFFFFU || emotion >= COMPANION_EMOTION_COUNT ||
        intensity > COMPANION_EMOTION_INTENSITY_STRONG || duration_ms < 5000U ||
        duration_ms > 15000U) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return ESP_ERR_TIMEOUT;
    s_role_color = rgb;
    companion_expression_engine_suggest_emotion(
        &s_expression_engine, emotion, intensity, duration_ms,
        (uint32_t)(esp_timer_get_time() / 1000LL));
    if (!expression_pack_is_active()) s_next_face_frame_us = esp_timer_get_time();
    xSemaphoreGive(s_board_mutex);
    return ESP_OK;
}

extern "C" esp_err_t companion_hardware_configure_expression_frame_rate(
    companion_expression_fps_mode_t mode, uint8_t min_fps, uint8_t max_fps)
{
    if (!s_initialized || (mode != COMPANION_EXPRESSION_FPS_FIXED &&
        mode != COMPANION_EXPRESSION_FPS_ADAPTIVE) || !supported_expression_fps(min_fps) ||
        !supported_expression_fps(max_fps) || min_fps > max_fps ||
        (mode == COMPANION_EXPRESSION_FPS_FIXED && min_fps != max_fps)) {
        return ESP_ERR_INVALID_ARG;
    }
    taskENTER_CRITICAL(&s_expression_lock);
    s_expression_fps_mode = mode;
    s_expression_min_fps = min_fps;
    s_expression_max_fps = max_fps;
    s_expression_diagnostics.target_fps = s_screensaver ? 20U : max_fps;
    s_expression_diagnostics.degrade_reason = s_screensaver ?
        COMPANION_EXPRESSION_DEGRADE_IDLE_SLEEP : COMPANION_EXPRESSION_DEGRADE_NONE;
    s_over_budget_frames = 0;
    s_stable_since_ms = (uint32_t)(esp_timer_get_time() / 1000LL);
    taskEXIT_CRITICAL(&s_expression_lock);
    s_next_face_frame_us = esp_timer_get_time();
    return ESP_OK;
}

extern "C" esp_err_t companion_hardware_preview_expression(
    companion_expression_preview_t preview, uint8_t value, uint32_t duration_ms)
{
    if (!s_initialized || expression_pack_is_active() ||
        preview <= COMPANION_EXPRESSION_PREVIEW_NONE ||
        preview > COMPANION_EXPRESSION_PREVIEW_UPDATING || duration_ms < 1000U ||
        duration_ms > 15000U) return ESP_ERR_INVALID_ARG;
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return ESP_ERR_TIMEOUT;
    companion_expression_engine_preview(&s_expression_engine, preview, value, duration_ms,
                                        (uint32_t)(esp_timer_get_time() / 1000LL));
    s_next_face_frame_us = esp_timer_get_time();
    xSemaphoreGive(s_board_mutex);
    return ESP_OK;
}

extern "C" void companion_hardware_get_expression_diagnostics(
    companion_expression_diagnostics_t *diagnostics)
{
    if (diagnostics == nullptr) return;
    taskENTER_CRITICAL(&s_expression_lock);
    *diagnostics = s_expression_diagnostics;
    taskEXIT_CRITICAL(&s_expression_lock);
    diagnostics->dynamic_renderer = s_dynamic_surface_active;
}

extern "C" void companion_hardware_set_expression_updating(bool updating)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return;
    companion_expression_engine_set_updating(
        &s_expression_engine, updating, (uint32_t)(esp_timer_get_time() / 1000LL));
    if (!expression_pack_is_active()) s_next_face_frame_us = esp_timer_get_time();
    xSemaphoreGive(s_board_mutex);
}
