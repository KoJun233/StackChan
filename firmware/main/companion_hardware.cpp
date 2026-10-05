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
#include "esp_attr.h"
#include "expression_engine.h"
#include "display_frame_policy.h"
#include "robot_eyes_renderer.h"
#include "device_ui.h"
#include "device_ui_font.h"
#include "expression_pack.h"
#include "lifecycle_clip_player.h"
#include "touch_interaction.h"
#include "ui_touch_ownership.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "freertos/idf_additions.h"
#include "src/misc/cache/instance/lv_image_cache.h"

#if defined(STACKCHAN_MEDIA003_EAF_PROBE) || defined(STACKCHAN_MEDIA003_EMOTE_PROBE)
#include "media003_backend_probe.h"
#endif

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
#define MIN_AMBIENT_BRIGHTNESS_PERCENT 18
#define MAX_AMBIENT_BRIGHTNESS_PERCENT 78
#define SCREENSAVER_BRIGHTNESS_PERCENT 9
#define SCREENSAVER_FRAME_MS 50
#define FACE_SURFACE_SIZE 160
#define FACE_SURFACE_X 80
#define FACE_SURFACE_Y 40
#define DEFAULT_ROLE_COLOR 0xFF4FA3
#define BUILTIN_FACE_SIZE 192
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
static int s_ambient_brightness_percent = NORMAL_BRIGHTNESS_PERCENT;

static lv_display_t *s_display;
static lv_indev_t *s_touch_indev;
static lv_obj_t *s_screen;
static lv_obj_t *s_eyes_root;
static lv_obj_t *s_static_image;
static lv_obj_t *s_capture_submit;
static bool s_capture_submit_visible;
static lv_obj_t *s_capture_submit_label;
static lv_obj_t *s_voice_mode_notice;
static uint32_t s_voice_mode_notice_expires_ms;
static bool s_voice_mode_visible;
static bool s_voice_mode_automatic = true;
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
static int s_manual_brightness_percent = -1;
static float s_local_gaze_x;
static float s_local_gaze_y;
static bool s_local_gaze_visible;
static bool s_collect_refresh_metrics;
static uint32_t s_refresh_flush_count;
static uint32_t s_refresh_flush_pixels;
static uint32_t s_refresh_flush_wait_us;
static int64_t s_refresh_flush_wait_started_us;
static uint32_t s_last_refresh_metrics_log_ms;
static uint32_t s_frames_in_window;
static uint32_t s_window_started_ms;
static display_frame_policy_t s_frame_policy;
static portMUX_TYPE s_transfer_lock=portMUX_INITIALIZER_UNLOCKED;
static uint32_t s_completed_frames;
static uint32_t s_completion_phase_generation;
static uint32_t s_completion_intervals[128];
static unsigned s_completion_count, s_completion_position;
static int64_t s_last_completed_us;
static int s_completion_phase=-1, s_queued_phase=-1, s_refresh_phase;
static uint32_t s_last_completion_diagnostics_ms;

extern "C" void __real_lv_display_flush_ready(lv_display_t *display);
extern "C" void IRAM_ATTR __wrap_lv_display_flush_ready(lv_display_t *display)
{
    if(display==s_display && lv_display_flush_is_last(display)) {
        int64_t now=esp_timer_get_time();
        bool in_isr=xPortInIsrContext();
        if(in_isr)taskENTER_CRITICAL_ISR(&s_transfer_lock);else taskENTER_CRITICAL(&s_transfer_lock);
        if(s_queued_phase>=0) {
            s_completed_frames++;
            if(s_completion_phase==s_queued_phase && s_last_completed_us>0) {
                uint64_t interval=static_cast<uint64_t>(now-s_last_completed_us);
                s_completion_intervals[s_completion_position++%128]=interval>UINT32_MAX?UINT32_MAX:static_cast<uint32_t>(interval);
                if(s_completion_count<128)s_completion_count++;
            } else {s_completion_count=0;s_completion_position=0;s_completion_phase_generation++;}
            s_completion_phase=s_queued_phase;s_last_completed_us=now;
        } else {
            if(s_completion_phase>=0)s_completion_phase_generation++;
            s_completion_phase=-1;s_last_completed_us=0;s_completion_count=0;s_completion_position=0;
        }
        if(in_isr)taskEXIT_CRITICAL_ISR(&s_transfer_lock);else taskEXIT_CRITICAL(&s_transfer_lock);
    }
    __real_lv_display_flush_ready(display);
}

static int compare_interval(const void *left,const void *right)
{
    uint32_t a=*static_cast<const uint32_t *>(left),b=*static_cast<const uint32_t *>(right);
    return a<b?-1:a>b?1:0;
}
static void log_frame_completion_diagnostics(uint32_t now_ms)
{
    if(now_ms-s_last_completion_diagnostics_ms<5000)return;
    static uint32_t intervals[128]; // Task only: keep 512 bytes off the UI stack.
    unsigned count;uint32_t completed,generation;int phase;
    taskENTER_CRITICAL(&s_transfer_lock);
    count=s_completion_count;completed=s_completed_frames;phase=s_completion_phase;
    generation=s_completion_phase_generation;
    memcpy(intervals,s_completion_intervals,count*sizeof(*intervals));
    taskEXIT_CRITICAL(&s_transfer_lock);
    qsort(intervals,count,sizeof(*intervals),compare_interval);
    companion_expression_diagnostics_t d;
    taskENTER_CRITICAL(&s_expression_lock);d=s_expression_diagnostics;taskEXIT_CRITICAL(&s_expression_lock);
    ESP_LOGI(TAG,"Expression frame diagnostics: phase=%d generation=%" PRIu32 " target=%u rendered_fps=%u completed=%" PRIu32
        " intervals=%u p50=%" PRIu32 " p95=%" PRIu32 " p99=%" PRIu32
        " audio_errors=%" PRIu32 " stack_free=%u internal_free=%u internal_largest=%u",
        phase,generation,d.target_fps,d.actual_fps,completed,count,count?intervals[(count*50+99)/100-1]:0,
        count?intervals[(count*95+99)/100-1]:0,count?intervals[(count*99+99)/100-1]:0,
        d.audio_underruns,static_cast<unsigned>(uxTaskGetStackHighWaterMark(nullptr)),
        static_cast<unsigned>(heap_caps_get_free_size(MALLOC_CAP_INTERNAL)),
        static_cast<unsigned>(heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL)));
    s_last_completion_diagnostics_ms=now_ms;
}
static bool s_audio_playback_active;
static bool s_audio_capture_active;
static bool s_expression_updating;
static bool s_hardware_error;
static float s_last_acceleration_sum;
static int64_t s_last_shake_us;
static int64_t s_next_input_poll_us;
static companion_expression_fps_mode_t s_expression_fps_mode = COMPANION_EXPRESSION_FPS_ADAPTIVE;
static uint8_t s_expression_min_fps = 30U;
static uint8_t s_expression_max_fps = 60U;
static companion_expression_behavior_t s_last_lifecycle_behavior = COMPANION_BEHAVIOR_NONE;

#if defined(STACKCHAN_MEDIA003_EAF_PROBE)
static bool s_media003_probe_active;
#endif

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
    // Configuration/state setters also own this mutex. A failed zero-wait
    // snapshot keeps the ordinary input deadline rather than reading torn 64 bits.
    bool snapshot=s_board_mutex!=nullptr && xSemaphoreTake(s_board_mutex,0)==pdTRUE;
    if(snapshot) {
        if (s_screensaver) {
            if (deadline_us <= 0 || (s_next_screensaver_frame_us > 0 &&
                s_next_screensaver_frame_us < deadline_us)) {
                deadline_us = s_next_screensaver_frame_us;
            }
        } else if (!device_ui_is_modal() && !expression_pack_is_active() &&
                   (deadline_us <= 0 || (s_next_face_frame_us > 0 &&
                    s_next_face_frame_us < deadline_us))) {
            deadline_us = s_next_face_frame_us;
        }
        xSemaphoreGive(s_board_mutex);
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
    lv_event_code_t code = lv_event_get_code(event);
    if(code==LV_EVENT_FLUSH_START && lv_display_flush_is_last(s_display)) {
        taskENTER_CRITICAL(&s_transfer_lock);
        s_queued_phase=s_collect_refresh_metrics?s_refresh_phase:-1;
        taskEXIT_CRITICAL(&s_transfer_lock);
    }
    if (!s_collect_refresh_metrics) return;
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

static bool take_mutex(SemaphoreHandle_t mutex, TickType_t timeout)
{
    return mutex != nullptr && xSemaphoreTake(mutex, timeout) == pdTRUE;
}

static void set_audio_playback_active(bool active)
{
    taskENTER_CRITICAL(&s_playback_lock);
    s_audio_playback_active = active;
    taskEXIT_CRITICAL(&s_playback_lock);
}

static void set_audio_capture_active(bool active)
{
    taskENTER_CRITICAL(&s_playback_lock);
    s_audio_capture_active = active;
    taskEXIT_CRITICAL(&s_playback_lock);
}

static int active_brightness_percent(void)
{
    return s_night_mode ? NIGHT_BRIGHTNESS_PERCENT :
           (s_manual_brightness_percent >= 0 ? s_manual_brightness_percent : s_ambient_brightness_percent);
}

static void restore_expression_fps_after_screensaver(void)
{
    taskENTER_CRITICAL(&s_expression_lock);
    s_expression_diagnostics.target_fps = s_expression_max_fps;
    s_expression_diagnostics.degrade_reason = COMPANION_EXPRESSION_DEGRADE_NONE;
    taskEXIT_CRITICAL(&s_expression_lock);
}

static void emit_touch_event(companion_touch_event_type_t type, int64_t occurred_us,
                            const lv_point_t &point)
{
    if (s_touch_event_queue == nullptr) return;
    // Moves never consume the last two slots reserved for press/release edges.
    if (type == COMPANION_TOUCH_MOVED && uxQueueSpacesAvailable(s_touch_event_queue) <= 2) return;
    companion_touch_event_t event = {
        .type = type, .occurred_us = occurred_us, .x = (int16_t)point.x, .y = (int16_t)point.y,
    };
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
    if (object == nullptr || lv_obj_has_flag(object, LV_OBJ_FLAG_HIDDEN) == hidden) return;
    if (hidden) lv_obj_add_flag(object, LV_OBJ_FLAG_HIDDEN);
    else lv_obj_remove_flag(object, LV_OBJ_FLAG_HIDDEN);
}

static esp_err_t create_expression_scene_locked(void)
{
    s_screen = lv_screen_active();
    lv_obj_remove_style_all(s_screen);
    lv_obj_set_style_bg_color(s_screen, lv_color_hex(0x000000), 0);
    lv_obj_set_style_bg_opa(s_screen, LV_OPA_COVER, 0);
    s_eyes_root = robot_eyes_renderer_create(s_screen);
    if (s_eyes_root == nullptr) return ESP_ERR_NO_MEM;
    lv_obj_set_pos(s_eyes_root, (BSP_LCD_H_RES-BUILTIN_FACE_SIZE)/2, (BSP_LCD_V_RES-BUILTIN_FACE_SIZE)/2);
    lv_obj_set_size(s_eyes_root, BUILTIN_FACE_SIZE, BUILTIN_FACE_SIZE);
    lv_obj_remove_flag(s_eyes_root, LV_OBJ_FLAG_OVERFLOW_VISIBLE);

    s_static_image = lv_image_create(s_screen);
    lv_obj_set_pos(s_static_image, 0, 0);
    lifecycle_clip_player_init(s_screen, FACE_SURFACE_X, FACE_SURFACE_Y);
    s_capture_submit = lv_obj_create(s_screen);
    lv_obj_remove_style_all(s_capture_submit);
    lv_obj_set_pos(s_capture_submit, 0, 208);
    lv_obj_set_size(s_capture_submit, 320, 32);
    lv_obj_remove_flag(s_capture_submit, LV_OBJ_FLAG_SCROLLABLE);
    s_capture_submit_label = lv_label_create(s_capture_submit);
    lv_label_set_text_static(s_capture_submit_label, "轻点结束 · 下滑取消");
    lv_obj_set_style_text_font(s_capture_submit_label, device_menu_font(), 0);
    lv_obj_set_style_text_color(s_capture_submit_label, lv_color_hex(0xFFFFFF), 0);
    lv_obj_center(s_capture_submit_label);
    lv_obj_remove_flag(s_capture_submit, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_remove_flag(s_capture_submit_label, LV_OBJ_FLAG_CLICKABLE);
    set_hidden(s_capture_submit, true);
    s_voice_mode_notice = lv_label_create(s_screen);
    lv_obj_set_style_text_color(s_voice_mode_notice, lv_color_hex(0x93A5B3), 0);
    lv_obj_remove_flag(s_voice_mode_notice, LV_OBJ_FLAG_CLICKABLE);
    set_hidden(s_voice_mode_notice, true);
    device_ui_create(s_screen);

#if defined(STACKCHAN_MEDIA003_EAF_PROBE) || defined(STACKCHAN_MEDIA003_EMOTE_PROBE)
    media003_backend_probe_init(s_screen, FACE_SURFACE_X, FACE_SURFACE_Y);
    media003_backend_probe_diagnostics_t probe = {};
    media003_backend_probe_get_diagnostics(&probe);
    ESP_LOGI(TAG, "MEDIA-003 backend=%s ready=%s asset=%" PRIu32
                  " init=%" PRIu32 " us heap_delta=%" PRIu32,
             probe.backend, probe.ready ? "true" : "false", probe.asset_bytes,
             probe.init_time_us, probe.heap_delta_bytes);
#endif

    set_hidden(s_static_image, true);
    return ESP_OK;
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
    lifecycle_clip_player_stop();
    s_last_lifecycle_behavior = COMPANION_BEHAVIOR_NONE;
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
    set_hidden(s_eyes_root, true);
    set_hidden(s_static_image, false);
    // Static packs stay below the modal/capture overlays created after them.
    lv_refr_now(s_display);
    return true;
}

static void update_dynamic_scene_locked(const companion_expression_pose_t &pose, uint32_t now_ms)
{
    set_hidden(s_static_image, true);
    lifecycle_clip_player_poll();
    companion_expression_behavior_t lifecycle_behavior = COMPANION_BEHAVIOR_NONE;
    bool interaction_active = s_expression_engine.updating ||
                              s_face_state == COMPANION_FACE_LISTENING ||
                              s_face_state == COMPANION_FACE_PROCESSING ||
                              s_face_state == COMPANION_FACE_SPEAKING ||
                              !s_connected || device_ui_is_modal();
    if (!interaction_active && s_expression_engine.preview == COMPANION_EXPRESSION_PREVIEW_BEHAVIOR &&
        now_ms < s_expression_engine.preview_expires_ms) {
        lifecycle_behavior = (companion_expression_behavior_t)s_expression_engine.preview_value;
    } else if (!interaction_active && s_expression_engine.behavior != COMPANION_BEHAVIOR_NONE &&
               now_ms < s_expression_engine.behavior_expires_ms) {
        lifecycle_behavior = s_expression_engine.behavior;
    }
    if (interaction_active) {
        lifecycle_clip_player_stop();
        s_last_lifecycle_behavior = COMPANION_BEHAVIOR_NONE;
    } else if (lifecycle_behavior != COMPANION_BEHAVIOR_NONE &&
               lifecycle_behavior != s_last_lifecycle_behavior) {
        lifecycle_clip_player_stop();
        expression_lifecycle_clip_t clip = EXPRESSION_LIFECYCLE_COUNT;
        if (lifecycle_behavior == COMPANION_BEHAVIOR_BOOT_APPEAR) clip = EXPRESSION_LIFECYCLE_BOOT_APPEAR;
        else if (lifecycle_behavior == COMPANION_BEHAVIOR_WAKE) clip = EXPRESSION_LIFECYCLE_WAKE;
        else if (lifecycle_behavior == COMPANION_BEHAVIOR_ROLE_SWITCH) clip = EXPRESSION_LIFECYCLE_ROLE_SWITCH;
        if (clip < EXPRESSION_LIFECYCLE_COUNT && expression_pack_has_lifecycle_clips()) {
            (void)lifecycle_clip_player_play(clip);
        }
        s_last_lifecycle_behavior = lifecycle_behavior;
    }
    bool lifecycle_active = lifecycle_clip_player_is_active();
    if (!lifecycle_active && lifecycle_behavior == COMPANION_BEHAVIOR_NONE) {
        s_last_lifecycle_behavior = COMPANION_BEHAVIOR_NONE;
    }
    set_hidden(s_eyes_root, lifecycle_active);
    if (lifecycle_active) return;
#if defined(STACKCHAN_MEDIA003_EAF_PROBE)
    bool eaf_active = s_expression_engine.behavior == COMPANION_BEHAVIOR_BOOT_APPEAR &&
                      now_ms < s_expression_engine.behavior_expires_ms;
    s_media003_probe_active = media003_backend_probe_set_active(eaf_active);
    set_hidden(s_eyes_root, s_media003_probe_active);
    if (s_media003_probe_active) return;
#else
    set_hidden(s_eyes_root, false);
#endif
    companion_expression_pose_t visible_pose = pose;
    if (s_local_gaze_visible && s_face_state == COMPANION_FACE_IDLE && s_connected &&
        !s_expression_engine.updating && !device_ui_is_modal() && !s_screensaver) {
        visible_pose.gaze_x = s_local_gaze_x;
        visible_pose.gaze_y = s_local_gaze_y;
    }
    robot_eyes_renderer_update(s_eyes_root, &visible_pose, s_role_color);
    robot_eyes_renderer_set_status(s_eyes_root,
        companion_interaction_visible_state(s_face_state, s_connected),
        s_expression_engine.updating, s_voice_mode_automatic);
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
    s_refresh_phase=s_screensaver?100:static_cast<int>(state);
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
    uint32_t skipped=0;
    s_next_face_frame_us=display_frame_next_deadline(s_screensaver?started_us:s_next_face_frame_us,finished_us,fps,&skipped);
    taskENTER_CRITICAL(&s_expression_lock);
    s_expression_diagnostics.dropped_frames+=skipped;
    taskEXIT_CRITICAL(&s_expression_lock);
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

static void update_expression_performance(int64_t now_us,
                                          uint32_t lock_wait_us, bool rendered)
{
    uint32_t now_ms = (uint32_t)(now_us / 1000LL);
    taskENTER_CRITICAL(&s_expression_lock);
    lock_wait_us += s_frame_display_lock_wait_us;
    s_frame_display_lock_wait_us = 0U;
    s_expression_diagnostics.display_lock_wait_us = lock_wait_us;
    uint32_t frame_time_us=s_expression_diagnostics.draw_time_us+s_expression_diagnostics.transfer_time_us;
    if(rendered)s_frames_in_window++;
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
        s_expression_diagnostics.target_fps=20;
        s_expression_diagnostics.degrade_reason=COMPANION_EXPRESSION_DEGRADE_IDLE_SLEEP;
        s_frame_policy.stable_since_ms=now_ms;
    } else {
        s_expression_diagnostics.target_fps=display_frame_adapt(&s_frame_policy,now_ms,
            s_expression_diagnostics.target_fps,s_expression_min_fps,s_expression_max_fps,
            s_expression_fps_mode==COMPANION_EXPRESSION_FPS_FIXED,rendered,
            frame_time_us,lock_wait_us,s_expression_diagnostics.audio_underruns,
            &s_expression_diagnostics.degrade_reason);
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

static esp_err_t set_display_brightness(int percent)
{
    esp_err_t err = bsp_display_brightness_set(percent);
    if (err != ESP_OK) ESP_LOGW(TAG, "Display brightness update failed: %s", esp_err_to_name(err));
    return err;
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
        if (!connected) {
            s_local_gaze_visible = false;
            s_local_gaze_x = 0.0f;
            s_local_gaze_y = 0.0f;
        }
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

static bool sample_touch(bool *touched, lv_point_t *point, uint32_t *lock_wait_us, bool *modal)
{
    if (touched == nullptr || lock_wait_us == nullptr) return false;
    int64_t started_us = esp_timer_get_time();
    if (!bsp_display_lock(20)) {
        *lock_wait_us += elapsed_us(started_us, esp_timer_get_time());
        return false;
    }
    *lock_wait_us += elapsed_us(started_us, esp_timer_get_time());
    device_ui_tick_locked();
    *modal = device_ui_is_modal();
    *touched = s_touch_indev != nullptr &&
               lv_indev_get_state(s_touch_indev) == LV_INDEV_STATE_PRESSED;
    if (s_touch_indev != nullptr) lv_indev_get_point(s_touch_indev, point);
    bool control_visible = s_capture_submit_visible && !s_expression_engine.updating && !*modal;
    set_hidden(s_capture_submit, !control_visible);
    if (control_visible) {
        lv_obj_move_to_index(s_capture_submit, -1);
    }
    uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000LL);
    bool notice_visible = s_voice_mode_visible && !*modal && !s_screensaver &&
                          !s_expression_engine.updating && s_face_state == COMPANION_FACE_IDLE &&
                          (int32_t)(now_ms - s_voice_mode_notice_expires_ms) < 0;
    set_hidden(s_voice_mode_notice, !notice_visible);
    if (notice_visible) {
        lv_label_set_text_static(s_voice_mode_notice, s_voice_mode_automatic ? "WAKE" : "PTT");
        lv_obj_align(s_voice_mode_notice, LV_ALIGN_BOTTOM_MID, 0, -12);
    }
    bsp_display_unlock();
    return true;
}

static void ui_task(void *argument)
{
    (void)argument;
    vTaskDelay(pdMS_TO_TICKS(UI_STARTUP_GRACE_MS));
    bool current_touch = false;
    ui_touch_ownership_t touch_owner = {};
    lv_point_t touch_point = {};
    for (;;) {
        int64_t now = esp_timer_get_time();
        bool touched = current_touch;
        bool input_sampled = false;
        bool screensaver_was_active = false;
        bool modal_sampled = device_ui_is_modal();
        uint32_t lock_wait_us = 0;
        int64_t lock_started_us = now;
        if (now >= s_next_input_poll_us && take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            lock_wait_us = elapsed_us(lock_started_us, esp_timer_get_time());
            input_sampled = sample_touch(&touched, &touch_point, &lock_wait_us, &modal_sampled);
            if (input_sampled) current_touch = touched;
            int64_t sensor_now = esp_timer_get_time();
            s_next_input_poll_us = sensor_now + (int64_t)INPUT_POLL_MS * 1000LL;
            poll_shake_sensor(sensor_now);
            screensaver_was_active = s_screensaver;
            xSemaphoreGive(s_board_mutex);
        }
        now = esp_timer_get_time();
        if (input_sampled) {
            ui_touch_route_t route = ui_touch_ownership_sample(&touch_owner, touched, modal_sampled,
                screensaver_was_active, (int16_t)touch_point.x, (int16_t)touch_point.y,
                (uint32_t)(now / 1000LL));
            if (route == UI_TOUCH_ROUTE_PRESS) emit_touch_event(COMPANION_TOUCH_PRESSED, now, touch_point);
            else if (route == UI_TOUCH_ROUTE_MOVE) emit_touch_event(COMPANION_TOUCH_MOVED, now, touch_point);
            else if (route == UI_TOUCH_ROUTE_RELEASE) emit_touch_event(COMPANION_TOUCH_RELEASED, now, touch_point);
        }
        if (input_sampled && touched) companion_hardware_mark_activity();

        bool idle = now - last_activity_us() >=
                    (int64_t)CONFIG_STACKCHAN_SCREENSAVER_IDLE_SECONDS * 1000LL * 1000LL;
        bool rendered = false;
        lock_started_us = esp_timer_get_time();
        if (take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            lock_wait_us += elapsed_us(lock_started_us, esp_timer_get_time());
            companion_face_state_t visible = visible_state_locked();
            bool modal_active = device_ui_is_modal();
            if (idle && !modal_active && companion_interaction_allows_screensaver(visible) && !s_screensaver) {
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
            } else if (s_screensaver && (modal_active || !companion_interaction_allows_screensaver(visible))) {
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
            } else if (!modal_active && !s_screensaver && !expression_pack_is_active() &&
                       now >= s_next_face_frame_us) {
                draw_face_locked(visible);
                rendered = true;
            }
            xSemaphoreGive(s_board_mutex);
        }
        update_expression_performance(esp_timer_get_time(),
                                      lock_wait_us, rendered);
        log_frame_completion_diagnostics(static_cast<uint32_t>(esp_timer_get_time()/1000LL));
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
    // Display/event work never reads or writes NVS. Keep the separate Flash
    // worker internal; move only these task stacks, never the DMA buffers.
    display_config.lvgl_port_cfg.task_stack_caps = MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT;
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
    esp_err_t scene_err = create_expression_scene_locked();
    bsp_display_unlock();
    if (scene_err != ESP_OK) return scene_err;
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
    if (xTaskCreatePinnedToCoreWithCaps(ui_task, "companion_ui", UI_TASK_STACK_SIZE, nullptr,
                                UI_TASK_PRIORITY, &s_ui_task_handle, UI_TASK_CORE,
                                MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT) != pdPASS) {
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
    set_audio_capture_active(true);
    esp_err_t err = open_microphone();
    if (err == ESP_OK) {
        err = esp_codec_dev_read(s_microphone_codec, samples, sample_count * sizeof(int16_t));
    }
    set_audio_capture_active(false);
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

extern "C" esp_err_t companion_hardware_set_ambient_brightness(int brightness_percent)
{
    if (!s_initialized || brightness_percent < MIN_AMBIENT_BRIGHTNESS_PERCENT ||
        brightness_percent > MAX_AMBIENT_BRIGHTNESS_PERCENT) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) return ESP_ERR_TIMEOUT;
    s_ambient_brightness_percent = brightness_percent;
    esp_err_t err = ESP_OK;
    if (!s_night_mode && !s_screensaver && s_manual_brightness_percent < 0)
        err = set_display_brightness(active_brightness_percent());
    xSemaphoreGive(s_board_mutex);
    return err;
}

extern "C" esp_err_t companion_hardware_set_local_brightness(int brightness_percent)
{
    if (!s_initialized || brightness_percent < 10 || brightness_percent > 100) return ESP_ERR_INVALID_ARG;
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return ESP_ERR_TIMEOUT;
    int previous = s_manual_brightness_percent;
    s_manual_brightness_percent = brightness_percent;
    esp_err_t err = s_screensaver ? ESP_OK : set_display_brightness(active_brightness_percent());
    if (err != ESP_OK) s_manual_brightness_percent = previous;
    xSemaphoreGive(s_board_mutex);
    return err;
}

extern "C" esp_err_t companion_hardware_clear_local_brightness(void)
{
    if (!s_initialized) return ESP_ERR_INVALID_STATE;
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return ESP_ERR_TIMEOUT;
    int previous = s_manual_brightness_percent;
    s_manual_brightness_percent = -1;
    esp_err_t err = s_screensaver ? ESP_OK : set_display_brightness(active_brightness_percent());
    if (err != ESP_OK) s_manual_brightness_percent = previous;
    xSemaphoreGive(s_board_mutex);
    return err;
}

extern "C" int companion_hardware_get_local_brightness(void)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) return -1;
    int brightness = s_manual_brightness_percent;
    xSemaphoreGive(s_board_mutex);
    return brightness;
}

extern "C" bool companion_hardware_is_idle(void)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(50))) return false;
    bool audio_busy;
    taskENTER_CRITICAL(&s_playback_lock);
    // Passive WakeNet microphone sampling is compatible with face-idle capture.
    // A real voice turn is excluded below, and the final body guard still uses
    // get_motion_guard() after pausing the microphone listener.
    audio_busy = s_audio_playback_active || s_expression_updating || s_hardware_error;
    taskEXIT_CRITICAL(&s_playback_lock);
    bool idle = s_connected && s_face_state == COMPANION_FACE_IDLE && !s_screensaver &&
                !s_expression_engine.turn_active && !s_expression_engine.updating &&
                !device_ui_is_modal() && !audio_busy;
    xSemaphoreGive(s_board_mutex);
    return idle;
}

extern "C" void companion_hardware_set_local_gaze(float x, float y, bool visible)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(50))) return;
    if (!std::isfinite(x) || !std::isfinite(y)) visible = false;
    s_local_gaze_x = visible ? fmaxf(-1.0f, fminf(1.0f, x)) : 0.0f;
    s_local_gaze_y = visible ? fmaxf(-1.0f, fminf(1.0f, y)) : 0.0f;
    s_local_gaze_visible = visible;
    s_next_face_frame_us = esp_timer_get_time();
    xSemaphoreGive(s_board_mutex);
    ui_wake_timer_callback(nullptr);
}

extern "C" bool companion_hardware_wait_touch_event(companion_touch_event_t *event,
                                                       uint32_t timeout_ms)
{
    if (event == nullptr || s_touch_event_queue == nullptr) return false;
    TickType_t timeout = pdMS_TO_TICKS(timeout_ms);
    if (timeout_ms > 0 && timeout == 0) timeout = 1;
    return xQueueReceive(s_touch_event_queue, event, timeout) == pdTRUE;
}

extern "C" void companion_hardware_show_capture_submit(bool visible)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return;
    s_capture_submit_visible = visible;
    xSemaphoreGive(s_board_mutex);
    ui_wake_timer_callback(nullptr);
}

extern "C" void companion_hardware_show_voice_input_mode(bool visible, bool automatic)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return;
    bool changed = s_voice_mode_automatic != automatic;
    s_voice_mode_visible = visible;
    s_voice_mode_automatic = automatic;
    if (changed) {
        uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000LL);
        s_voice_mode_notice_expires_ms = now_ms + 1600U;
        companion_expression_engine_trigger(&s_expression_engine, COMPANION_BEHAVIOR_ROLE_SWITCH, 1500U, now_ms);
        s_next_face_frame_us = esp_timer_get_time();
    }
    xSemaphoreGive(s_board_mutex);
    ui_wake_timer_callback(nullptr);
}

extern "C" void companion_hardware_respond_to_top_touch(void)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(50))) return;
    bool eligible = visible_state_locked() == COMPANION_FACE_IDLE && !s_expression_engine.updating;
    if (eligible) {
        companion_expression_engine_suggest_emotion(&s_expression_engine, COMPANION_EMOTION_LOVING,
                COMPANION_EMOTION_INTENSITY_WEAK, 5000U, (uint32_t)(esp_timer_get_time() / 1000LL));
        s_next_face_frame_us = esp_timer_get_time();
    }
    xSemaphoreGive(s_board_mutex);
    if (eligible) companion_hardware_mark_activity();
}

extern "C" void companion_hardware_set_body_emotion(companion_emotion_t emotion)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(50))) return;
    companion_expression_engine_set_body_emotion(&s_expression_engine, emotion,
            (uint32_t)(esp_timer_get_time() / 1000LL));
    s_next_face_frame_us = esp_timer_get_time();
    xSemaphoreGive(s_board_mutex);
}

extern "C" esp_err_t companion_hardware_configure_expression(
    uint32_t rgb, companion_emotion_t emotion,
    companion_emotion_intensity_t intensity, uint32_t duration_ms)
{
    if (!s_initialized || rgb > 0xFFFFFFU || emotion < COMPANION_EMOTION_NEUTRAL ||
        emotion >= COMPANION_EMOTION_COUNT || intensity < COMPANION_EMOTION_INTENSITY_WEAK ||
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

static esp_err_t take_expression_locks(void)
{
    if (!s_initialized) return ESP_ERR_INVALID_STATE;
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) return ESP_ERR_TIMEOUT;
    if (!bsp_display_lock(500)) {
        xSemaphoreGive(s_board_mutex);
        return ESP_ERR_TIMEOUT;
    }
    return ESP_OK;
}

static void release_expression_locks(void)
{
    s_next_face_frame_us = esp_timer_get_time();
    bsp_display_unlock();
    xSemaphoreGive(s_board_mutex);
    ui_wake_timer_callback(nullptr);
}

extern "C" void companion_hardware_expression_turn_begin(const char *turn_id)
{
    if (take_expression_locks() != ESP_OK) return;
    companion_expression_engine_turn_begin(&s_expression_engine, turn_id,
        (uint32_t)(esp_timer_get_time() / 1000LL));
    release_expression_locks();
}

extern "C" esp_err_t companion_hardware_configure_turn_expression(uint32_t rgb,
    companion_emotion_t emotion, companion_emotion_intensity_t intensity,
    uint32_t duration_ms, const char *turn_id)
{
    if (rgb > 0xFFFFFFU || emotion < COMPANION_EMOTION_NEUTRAL || emotion >= COMPANION_EMOTION_COUNT ||
        intensity < COMPANION_EMOTION_INTENSITY_WEAK || intensity > COMPANION_EMOTION_INTENSITY_STRONG ||
        duration_ms < 5000U || duration_ms > 15000U || turn_id == nullptr) return ESP_ERR_INVALID_ARG;
    esp_err_t err = take_expression_locks();
    if (err != ESP_OK) return err;
    bool accepted = companion_expression_engine_turn_emotion(&s_expression_engine, turn_id,
        emotion, intensity, duration_ms, (uint32_t)(esp_timer_get_time() / 1000LL));
    if (accepted) s_role_color = rgb;
    release_expression_locks();
    return accepted ? ESP_OK : ESP_ERR_INVALID_STATE;
}

extern "C" void companion_hardware_expression_turn_playback_started(const char *turn_id)
{
    if (take_expression_locks() != ESP_OK) return;
    companion_expression_engine_turn_playback_started(&s_expression_engine, turn_id,
        (uint32_t)(esp_timer_get_time() / 1000LL));
    release_expression_locks();
}

extern "C" void companion_hardware_expression_turn_end(const char *turn_id)
{
    if (take_expression_locks() != ESP_OK) return;
    companion_expression_engine_turn_end(&s_expression_engine, turn_id,
        (uint32_t)(esp_timer_get_time() / 1000LL));
    release_expression_locks();
}

extern "C" void companion_hardware_expression_turn_cancel(const char *turn_id)
{
    if (take_expression_locks() != ESP_OK) return;
    companion_expression_engine_turn_cancel(&s_expression_engine, turn_id,
        (uint32_t)(esp_timer_get_time() / 1000LL));
    release_expression_locks();
}

extern "C" void companion_hardware_expression_clear_turn(void)
{
    if (take_expression_locks() != ESP_OK) return;
    companion_expression_engine_clear_turn(&s_expression_engine,
        (uint32_t)(esp_timer_get_time() / 1000LL));
    release_expression_locks();
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
    if(!take_mutex(s_board_mutex,pdMS_TO_TICKS(250)))return ESP_ERR_TIMEOUT;
    taskENTER_CRITICAL(&s_expression_lock);
    s_expression_fps_mode = mode;
    s_expression_min_fps = min_fps;
    s_expression_max_fps = max_fps;
    s_expression_diagnostics.target_fps = s_screensaver ? 20U : max_fps;
    s_expression_diagnostics.degrade_reason = s_screensaver ?
        COMPANION_EXPRESSION_DEGRADE_IDLE_SLEEP : COMPANION_EXPRESSION_DEGRADE_NONE;
    s_frame_policy.over_budget_frames=0;
    s_frame_policy.stable_since_ms=(uint32_t)(esp_timer_get_time()/1000LL);
    taskEXIT_CRITICAL(&s_expression_lock);
    s_next_face_frame_us = esp_timer_get_time();
    xSemaphoreGive(s_board_mutex);
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

extern "C" void companion_hardware_get_motion_guard(bool *audio_busy,
                                                       bool *updating,
                                                       bool *device_error)
{
    taskENTER_CRITICAL(&s_playback_lock);
    if (audio_busy != nullptr) {
        *audio_busy = s_audio_playback_active || s_audio_capture_active;
    }
    if (updating != nullptr) *updating = s_expression_updating;
    if (device_error != nullptr) *device_error = s_hardware_error;
    taskEXIT_CRITICAL(&s_playback_lock);
}

extern "C" void companion_hardware_set_expression_updating(bool updating)
{
    taskENTER_CRITICAL(&s_playback_lock);
    s_expression_updating = updating;
    taskEXIT_CRITICAL(&s_playback_lock);
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) return;
    companion_expression_engine_set_updating(
        &s_expression_engine, updating, (uint32_t)(esp_timer_get_time() / 1000LL));
    if (!expression_pack_is_active()) s_next_face_frame_us = esp_timer_get_time();
    xSemaphoreGive(s_board_mutex);
}
