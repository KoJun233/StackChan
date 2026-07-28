#include "companion_hardware.h"

#include <M5Unified.h>

#include <cstdlib>

#include "audio_wav.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "expression_pack.h"
#include "face_animation.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "screensaver_motion.h"

#define UI_TASK_STACK_SIZE 4096
#define UI_TASK_PRIORITY 3
#define UI_POLL_MS 50
#define TOUCH_EVENT_QUEUE_LENGTH 8
#define NORMAL_BRIGHTNESS 160
#define NIGHT_BRIGHTNESS 64
#define SCREENSAVER_BRIGHTNESS 24
#define SCREENSAVER_FRAME_MS 2500
#define FACE_ANIMATION_FRAME_MS 100
#define AUDIO_WAIT_MARGIN_MS 3000
#define LEFT_PUPIL_X 100
#define RIGHT_PUPIL_X 220
#define PUPIL_Y 92
#define PUPIL_RADIUS 12
#define EYE_COLOR 0xFFFFFF
#define PUPIL_COLOR 0x101018

static const char *TAG = "companion_hardware";
static SemaphoreHandle_t s_board_mutex;
static SemaphoreHandle_t s_audio_mutex;
static QueueHandle_t s_touch_event_queue;
static portMUX_TYPE s_activity_lock = portMUX_INITIALIZER_UNLOCKED;
static portMUX_TYPE s_playback_lock = portMUX_INITIALIZER_UNLOCKED;
static int64_t s_last_activity_us;
static companion_face_state_t s_face_state = COMPANION_FACE_IDLE;
static bool s_connected;
static bool s_screensaver;
static size_t s_screensaver_frame;
static int64_t s_next_screensaver_frame_us;
static int64_t s_face_started_us;
static int64_t s_next_face_frame_us;
static screensaver_pupil_offset_t s_pupil_offset;
static bool s_initialized;
static bool s_playback_stop_requested;
static int s_volume_percent = 50;
static bool s_night_mode;
static M5Canvas s_face_canvas(&M5.Display);
static bool s_face_canvas_ready;

static uint8_t active_brightness(void)
{
    return s_night_mode ? NIGHT_BRIGHTNESS : NORMAL_BRIGHTNESS;
}

static void emit_touch_event(companion_touch_event_type_t type, int64_t occurred_us)
{
    if (s_touch_event_queue == nullptr) {
        return;
    }
    companion_touch_event_t event = {
        .type = type,
        .occurred_us = occurred_us,
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

static bool take_mutex(SemaphoreHandle_t mutex, TickType_t timeout)
{
    return mutex != nullptr && xSemaphoreTake(mutex, timeout) == pdTRUE;
}

template <typename Display>
static void draw_eye(Display &display,
                     int center_x,
                     const companion_face_frame_t &frame,
                     companion_face_state_t state,
                     uint32_t accent)
{
    constexpr int eye_width = 90;
    constexpr int eye_height = 64;
    int visible_height = (eye_height * frame.eye_open_percent) / 100;
    if (visible_height < 8) {
        visible_height = 8;
    }
    int top = PUPIL_Y - visible_height / 2;
    int left = center_x - eye_width / 2;
    int radius = visible_height / 2;
    if (radius > 22) {
        radius = 22;
    }
    display.fillRoundRect(left, top, eye_width, visible_height, radius, EYE_COLOR);

    if (state == COMPANION_FACE_SUCCESS) {
        display.fillTriangle(left, top + visible_height,
                             left + eye_width / 2, top + visible_height / 2,
                             left + eye_width, top + visible_height,
                             0x000000);
    } else if (state == COMPANION_FACE_RECOVERABLE_ERROR) {
        if (center_x < 160) {
            display.fillTriangle(left, top, left + eye_width, top, left + eye_width, top + 20, 0x000000);
        } else {
            display.fillTriangle(left, top, left + eye_width, top, left, top + 20, 0x000000);
        }
    } else if (state == COMPANION_FACE_NO_SPEECH || state == COMPANION_FACE_OFFLINE) {
        display.fillRect(left, top, eye_width, visible_height / 3, 0x000000);
    }

    if (visible_height >= 24) {
        int pupil_x = center_x + frame.gaze_x;
        int pupil_y = PUPIL_Y + frame.gaze_y;
        display.fillCircle(pupil_x, pupil_y, PUPIL_RADIUS, PUPIL_COLOR);
        display.fillCircle(pupil_x - 4, pupil_y - 5, 3, 0xFFFFFF);
        if (state == COMPANION_FACE_LISTENING) {
            display.drawCircle(pupil_x, pupil_y, PUPIL_RADIUS + 5, accent);
        }
    }
}

template <typename Display>
static void draw_builtin_face(Display &display,
                              companion_face_state_t state,
                              const companion_face_frame_t &frame)
{
    uint32_t background = 0x000000;
    uint32_t accent = 0xFF4FA3;
    uint32_t status = 0x42D392;
    switch (state) {
        case COMPANION_FACE_LISTENING:
            status = 0x4BA3FF;
            accent = 0x4BA3FF;
            break;
        case COMPANION_FACE_PROCESSING:
            status = 0xFFD166;
            accent = 0xFFD166;
            break;
        case COMPANION_FACE_SPEAKING:
            status = 0xFF4FA3;
            accent = 0xFF4FA3;
            break;
        case COMPANION_FACE_SUCCESS:
            status = 0x42D392;
            accent = 0x42D392;
            break;
        case COMPANION_FACE_NO_SPEECH:
            status = 0x71D6C5;
            accent = 0x71D6C5;
            break;
        case COMPANION_FACE_OFFLINE:
            status = 0x8C8C8C;
            accent = 0x8C8C8C;
            break;
        case COMPANION_FACE_RECOVERABLE_ERROR:
            status = 0xFF8A3D;
            accent = 0xFF8A3D;
            break;
        case COMPANION_FACE_IDLE:
        default:
            break;
    }

    display.fillScreen(background);
    draw_eye(display, LEFT_PUPIL_X, frame, state, accent);
    draw_eye(display, RIGHT_PUPIL_X, frame, state, accent);

    if (state == COMPANION_FACE_LISTENING) {
        int ring = 4 + (frame.activity_percent * 7) / 100;
        display.drawCircle(28, 92, ring, accent);
        display.drawCircle(292, 92, ring, accent);
    } else if (state == COMPANION_FACE_PROCESSING) {
        int active_dot = (frame.activity_percent * 3) / 101;
        for (int index = 0; index < 3; index++) {
            display.fillCircle(146 + index * 14, 176, index == active_dot ? 6 : 3, accent);
        }
    }

    if (state == COMPANION_FACE_SUCCESS) {
        display.drawArc(160, 153, 33, 28, 22, 158, accent);
        display.fillCircle(128, 151, 3, accent);
        display.fillCircle(192, 151, 3, accent);
    } else if (state == COMPANION_FACE_RECOVERABLE_ERROR) {
        int pulse = 4 + (frame.activity_percent * 4) / 100;
        display.fillTriangle(160, 145, 136, 187, 184, 187, accent);
        display.fillRect(157, 158, 6, 16, background);
        display.fillCircle(160, 180, pulse / 2, background);
    } else if (state == COMPANION_FACE_NO_SPEECH) {
        display.drawArc(160, 178, 18, 14, 200, 340, accent);
        display.fillCircle(201, 138, 3, accent);
        display.fillCircle(211, 146, 2, accent);
    } else if (state == COMPANION_FACE_OFFLINE) {
        display.fillRoundRect(137, 167, 46, 6, 3, accent);
        display.drawCircle(160, 190, 5, accent);
    } else if (state == COMPANION_FACE_LISTENING) {
        int mouth_radius = 12 + (frame.activity_percent * 5) / 100;
        display.drawCircle(160, 169, mouth_radius, accent);
    } else {
        if (state == COMPANION_FACE_SPEAKING) {
            int mouth_height = 6 + (frame.mouth_open_percent * 28) / 100;
            display.fillRoundRect(130, 160 - mouth_height / 2, 60, mouth_height, mouth_height / 2, accent);
            if (mouth_height > 18) {
                display.fillRoundRect(139, 164, 42, 7, 3, 0xFFB4D7);
            }
        } else if (state == COMPANION_FACE_IDLE) {
            display.drawArc(160, 151, 27, 21, 25, 155, accent);
        }
    }
    display.fillCircle(302, 18, 8, status);
    display.drawCircle(302, 18, 11, status);
}

static void draw_builtin_face_locked(companion_face_state_t state)
{
    int64_t now = esp_timer_get_time();
    uint32_t elapsed_ms = s_face_started_us > 0 && now > s_face_started_us
                              ? (uint32_t)((now - s_face_started_us) / 1000LL)
                              : 0;
    companion_face_frame_t frame = companion_face_animation_frame(state, elapsed_ms);
    if (s_face_canvas_ready) {
        draw_builtin_face(s_face_canvas, state, frame);
        s_face_canvas.pushSprite(0, 0);
    } else {
        draw_builtin_face(M5.Display, state, frame);
    }
    s_pupil_offset = screensaver_motion_offset(0);
    s_screensaver_frame = 0;
    s_next_face_frame_us = now + (int64_t)FACE_ANIMATION_FRAME_MS * 1000LL;
}

static void draw_face_locked(companion_face_state_t state)
{
    uint8_t *image = nullptr;
    size_t image_size = 0;
    bool rendered = false;
    if (expression_pack_read_state(state, &image, &image_size) == ESP_OK) {
        if (s_face_canvas_ready) {
            s_face_canvas.fillScreen(0x000000);
            rendered = s_face_canvas.drawPng(image, image_size, 0, 0);
            if (rendered) {
                s_face_canvas.pushSprite(0, 0);
            }
        } else {
            rendered = M5.Display.drawPng(image, image_size, 0, 0);
        }
        free(image);
    }
    if (!rendered) {
        draw_builtin_face_locked(state);
        return;
    }
    int64_t now = esp_timer_get_time();
    s_pupil_offset = screensaver_motion_offset(0);
    s_screensaver_frame = 0;
    s_next_face_frame_us = now + (int64_t)FACE_ANIMATION_FRAME_MS * 1000LL;
}

static companion_face_state_t visible_state_locked(void)
{
    return companion_interaction_visible_state(s_face_state, s_connected);
}

static void draw_screensaver_pupils_locked(screensaver_pupil_offset_t next)
{
    M5.Display.fillCircle(LEFT_PUPIL_X + s_pupil_offset.x,
                          PUPIL_Y + s_pupil_offset.y,
                          PUPIL_RADIUS,
                          EYE_COLOR);
    M5.Display.fillCircle(RIGHT_PUPIL_X + s_pupil_offset.x,
                          PUPIL_Y + s_pupil_offset.y,
                          PUPIL_RADIUS,
                          EYE_COLOR);
    M5.Display.fillCircle(LEFT_PUPIL_X + next.x,
                          PUPIL_Y + next.y,
                          PUPIL_RADIUS,
                          PUPIL_COLOR);
    M5.Display.fillCircle(RIGHT_PUPIL_X + next.x,
                          PUPIL_Y + next.y,
                          PUPIL_RADIUS,
                          PUPIL_COLOR);
    s_pupil_offset = next;
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

extern "C" void companion_hardware_mark_activity(void)
{
    set_last_activity_now();
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(250))) {
        return;
    }
    if (s_screensaver) {
        s_screensaver = false;
        M5.Display.setBrightness(active_brightness());
        draw_face_locked(visible_state_locked());
    }
    xSemaphoreGive(s_board_mutex);
}

extern "C" void companion_hardware_set_state(companion_face_state_t state)
{
    set_last_activity_now();
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) {
        return;
    }
    s_face_state = state;
    s_face_started_us = esp_timer_get_time();
    s_screensaver = false;
    M5.Display.setBrightness(active_brightness());
    draw_face_locked(visible_state_locked());
    xSemaphoreGive(s_board_mutex);
}

extern "C" void companion_hardware_refresh_face(void)
{
    if (!s_initialized || !take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) {
        return;
    }
    s_face_started_us = esp_timer_get_time();
    s_screensaver = false;
    M5.Display.setBrightness(active_brightness());
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
    if (!take_mutex(s_board_mutex, portMAX_DELAY)) {
        return;
    }
    if (s_connected != connected || s_screensaver) {
        s_connected = connected;
        s_face_started_us = esp_timer_get_time();
        s_screensaver = false;
        M5.Display.setBrightness(active_brightness());
        draw_face_locked(visible_state_locked());
    }
    xSemaphoreGive(s_board_mutex);
}

static void ui_task(void *argument)
{
    (void)argument;
    bool previously_touched = false;
    bool consume_current_touch = false;
    for (;;) {
        bool touched = false;
        bool screensaver_was_active = false;
        if (take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            M5.update();
            touched = M5.Touch.getCount() > 0;
            screensaver_was_active = s_screensaver;
            xSemaphoreGive(s_board_mutex);
        }
        int64_t now = esp_timer_get_time();
        if (touched && !previously_touched) {
            consume_current_touch = screensaver_was_active;
            if (!consume_current_touch) {
                emit_touch_event(COMPANION_TOUCH_PRESSED, now);
            }
        } else if (!touched && previously_touched) {
            if (!consume_current_touch) {
                emit_touch_event(COMPANION_TOUCH_RELEASED, now);
            }
            consume_current_touch = false;
        }
        previously_touched = touched;
        if (touched) {
            companion_hardware_mark_activity();
        }

        bool idle = now - last_activity_us() >=
                    (int64_t)CONFIG_STACKCHAN_SCREENSAVER_IDLE_SECONDS * 1000LL * 1000LL;
        if (take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            companion_face_state_t visible = visible_state_locked();
            if (idle && companion_interaction_allows_screensaver(visible) && !s_screensaver) {
                s_face_started_us = now;
                draw_builtin_face_locked(visible);
                s_screensaver = true;
                s_screensaver_frame = 1;
                s_next_screensaver_frame_us = now + (int64_t)SCREENSAVER_FRAME_MS * 1000LL;
                M5.Display.setBrightness(SCREENSAVER_BRIGHTNESS);
                ESP_LOGI(TAG,
                         "Idle low-brightness pupil screensaver active; touch, voice, or reminder will wake it");
            } else if (s_screensaver && !companion_interaction_allows_screensaver(visible)) {
                s_screensaver = false;
                M5.Display.setBrightness(active_brightness());
                draw_face_locked(visible);
            } else if (s_screensaver && now >= s_next_screensaver_frame_us) {
                draw_screensaver_pupils_locked(screensaver_motion_offset(s_screensaver_frame));
                s_screensaver_frame++;
                s_next_screensaver_frame_us = now + (int64_t)SCREENSAVER_FRAME_MS * 1000LL;
            } else if (!s_screensaver && !expression_pack_is_active() &&
                       companion_face_animation_is_dynamic(visible) &&
                       now >= s_next_face_frame_us) {
                draw_face_locked(visible);
            }
            xSemaphoreGive(s_board_mutex);
        }
        vTaskDelay(pdMS_TO_TICKS(UI_POLL_MS));
    }
}

extern "C" esp_err_t companion_hardware_init(void)
{
    if (s_initialized) {
        return ESP_OK;
    }
    s_board_mutex = xSemaphoreCreateMutex();
    s_audio_mutex = xSemaphoreCreateMutex();
    s_touch_event_queue = xQueueCreate(TOUCH_EVENT_QUEUE_LENGTH, sizeof(companion_touch_event_t));
    if (s_board_mutex == nullptr || s_audio_mutex == nullptr || s_touch_event_queue == nullptr) {
        return ESP_ERR_NO_MEM;
    }

    auto config = M5.config();
    config.fallback_board = m5::board_t::board_M5StackCoreS3;
    config.internal_imu = false;
    config.external_imu = false;
    config.internal_mic = true;
    config.internal_spk = true;
    config.led_brightness = 0;
    M5.begin(config);
    M5.Display.setRotation(1);
    M5.Display.setBrightness(active_brightness());
    s_face_canvas.setColorDepth(16);
    s_face_canvas_ready = s_face_canvas.createSprite(M5.Display.width(), M5.Display.height()) != nullptr;
    if (!s_face_canvas_ready) {
        ESP_LOGW(TAG, "Face canvas unavailable; using direct display fallback");
    }
    M5.Speaker.setVolume((uint8_t)((s_volume_percent * 255 + 50) / 100));
    M5.Speaker.end();
    if (!M5.Mic.begin()) {
        return ESP_FAIL;
    }

    s_last_activity_us = esp_timer_get_time();
    s_face_started_us = s_last_activity_us;
    s_initialized = true;
    draw_face_locked(visible_state_locked());
    if (xTaskCreate(ui_task, "companion_ui", UI_TASK_STACK_SIZE, nullptr, UI_TASK_PRIORITY, nullptr) != pdPASS) {
        s_initialized = false;
        return ESP_ERR_NO_MEM;
    }
    ESP_LOGI(TAG, "CoreS3 display, touch, microphone, and speaker initialized; actuators remain disabled");
    return ESP_OK;
}

static esp_err_t wait_for_recording(size_t sample_count, uint32_t sample_rate)
{
    uint32_t duration_ms = (uint32_t)((sample_count * 1000ULL + sample_rate - 1) / sample_rate);
    int64_t deadline = esp_timer_get_time() + (int64_t)(duration_ms + AUDIO_WAIT_MARGIN_MS) * 1000LL;
    // Let M5Unified's lower-priority microphone task consume the queued request.
    // A short WakeNet frame may already be complete when this task runs again;
    // that is a successful completion, not a missing "started" transition.
    vTaskDelay(1);
    while (M5.Mic.isRecording() > 0) {
        if (esp_timer_get_time() >= deadline) {
            return ESP_ERR_TIMEOUT;
        }
        vTaskDelay(1);
    }
    return ESP_OK;
}

extern "C" esp_err_t companion_hardware_record_pcm(int16_t *samples,
                                                     size_t sample_count,
                                                     uint32_t sample_rate)
{
    if (!s_initialized || samples == nullptr || sample_count == 0 || sample_rate != 16000) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_audio_mutex, portMAX_DELAY)) {
        return ESP_ERR_TIMEOUT;
    }
    bool queued = false;
    if (take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) {
        M5.Speaker.end();
        queued = M5.Mic.begin() && M5.Mic.record(samples, sample_count, sample_rate, false);
        xSemaphoreGive(s_board_mutex);
    }
    esp_err_t err = queued ? wait_for_recording(sample_count, sample_rate) : ESP_FAIL;
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
    audio_wav_view_t view{};
    if (!s_initialized || !audio_wav_parse(wav, wav_size, &view)) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_audio_mutex, portMAX_DELAY)) {
        return ESP_ERR_TIMEOUT;
    }
    if (cancelled != nullptr) {
        *cancelled = false;
    }
    clear_playback_stop_request();

    bool queued = false;
    if (take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) {
        while (M5.Mic.isRecording()) {
            xSemaphoreGive(s_board_mutex);
            vTaskDelay(pdMS_TO_TICKS(1));
            if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) {
                xSemaphoreGive(s_audio_mutex);
                return ESP_ERR_TIMEOUT;
            }
        }
        M5.Mic.end();
        M5.Speaker.setVolume((uint8_t)((s_volume_percent * 255 + 50) / 100));
        queued = M5.Speaker.begin() && M5.Speaker.playWav(wav, wav_size, 1, 0, true);
        xSemaphoreGive(s_board_mutex);
    }

    uint32_t bytes_per_frame = (uint32_t)view.channels * (uint32_t)(view.bits_per_sample / 8);
    uint32_t duration_ms = bytes_per_frame == 0
                               ? 0
                               : (uint32_t)((view.data_size * 1000ULL) /
                                            ((uint64_t)view.sample_rate * bytes_per_frame));
    int64_t deadline = esp_timer_get_time() + (int64_t)(duration_ms + AUDIO_WAIT_MARGIN_MS) * 1000LL;
    bool played = false;
    bool started = false;
    bool stopped = false;
    while (queued && esp_timer_get_time() < deadline) {
        if (playback_stop_requested()) {
            stopped = true;
            break;
        }
        bool playing = false;
        if (take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            playing = M5.Speaker.isPlaying();
            xSemaphoreGive(s_board_mutex);
        }
        if (playing) {
            started = true;
        } else if (started) {
            played = true;
            break;
        }
        vTaskDelay(pdMS_TO_TICKS(1));
    }

    bool microphone_restarted = false;
    if (take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) {
        M5.Speaker.stop();
        M5.Speaker.end();
        microphone_restarted = M5.Mic.begin();
        xSemaphoreGive(s_board_mutex);
    }
    xSemaphoreGive(s_audio_mutex);
    if (!microphone_restarted) {
        ESP_LOGE(TAG, "Microphone did not restart after speaker playback");
    }
    if (!microphone_restarted) {
        return ESP_ERR_INVALID_STATE;
    }
    if (cancelled != nullptr) {
        *cancelled = stopped;
    }
    if (stopped) {
        return ESP_OK;
    }
    return played ? ESP_OK : ESP_FAIL;
}

extern "C" void companion_hardware_request_playback_stop(void)
{
    taskENTER_CRITICAL(&s_playback_lock);
    s_playback_stop_requested = true;
    taskEXIT_CRITICAL(&s_playback_lock);
}

extern "C" esp_err_t companion_hardware_configure_interaction(int volume_percent, bool night_mode)
{
    if (!s_initialized || volume_percent < 0 || volume_percent > 100) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_board_mutex, pdMS_TO_TICKS(500))) {
        return ESP_ERR_TIMEOUT;
    }
    s_volume_percent = volume_percent;
    s_night_mode = night_mode;
    M5.Speaker.setVolume((uint8_t)((s_volume_percent * 255 + 50) / 100));
    if (!s_screensaver) {
        M5.Display.setBrightness(active_brightness());
    }
    xSemaphoreGive(s_board_mutex);
    return ESP_OK;
}

extern "C" bool companion_hardware_wait_touch_event(companion_touch_event_t *event,
                                                       uint32_t timeout_ms)
{
    if (event == nullptr || s_touch_event_queue == nullptr) {
        return false;
    }
    TickType_t timeout = pdMS_TO_TICKS(timeout_ms);
    if (timeout_ms > 0 && timeout == 0) {
        timeout = 1;
    }
    return xQueueReceive(s_touch_event_queue, event, timeout) == pdTRUE;
}
