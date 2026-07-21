#include "companion_hardware.h"

#include <M5Unified.h>

#include "audio_wav.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "screensaver_motion.h"

#define UI_TASK_STACK_SIZE 4096
#define UI_TASK_PRIORITY 3
#define UI_POLL_MS 50
#define NORMAL_BRIGHTNESS 160
#define SCREENSAVER_BRIGHTNESS 24
#define SCREENSAVER_FRAME_MS 2500
#define AUDIO_WAIT_MARGIN_MS 3000
#define LEFT_PUPIL_X 106
#define RIGHT_PUPIL_X 214
#define PUPIL_Y 100
#define PUPIL_RADIUS 13
#define EYE_COLOR 0xFFFFFF
#define PUPIL_COLOR 0x101018

static const char *TAG = "companion_hardware";
static SemaphoreHandle_t s_board_mutex;
static SemaphoreHandle_t s_audio_mutex;
static portMUX_TYPE s_activity_lock = portMUX_INITIALIZER_UNLOCKED;
static int64_t s_last_activity_us;
static companion_face_state_t s_face_state = COMPANION_FACE_IDLE;
static bool s_screensaver;
static size_t s_screensaver_frame;
static int64_t s_next_screensaver_frame_us;
static screensaver_pupil_offset_t s_pupil_offset;
static bool s_initialized;

static bool take_mutex(SemaphoreHandle_t mutex, TickType_t timeout)
{
    return mutex != nullptr && xSemaphoreTake(mutex, timeout) == pdTRUE;
}

static void draw_face_locked(companion_face_state_t state)
{
    uint32_t background = 0x000000;
    uint32_t eye = EYE_COLOR;
    uint32_t pupil = PUPIL_COLOR;
    uint32_t accent = 0xFF4FA3;
    uint32_t status = 0x42D392;
    switch (state) {
        case COMPANION_FACE_LISTENING:
            status = 0x4BA3FF;
            accent = 0x4BA3FF;
            break;
        case COMPANION_FACE_THINKING:
            status = 0xFFD166;
            accent = 0xFFD166;
            break;
        case COMPANION_FACE_SPEAKING:
            status = 0xFF4FA3;
            accent = 0xFF4FA3;
            break;
        case COMPANION_FACE_ERROR:
            status = 0xFF5C5C;
            accent = 0xFF5C5C;
            break;
        case COMPANION_FACE_IDLE:
        default:
            break;
    }

    M5.Display.fillScreen(background);
    M5.Display.fillCircle(106, 94, 35, eye);
    M5.Display.fillCircle(214, 94, 35, eye);
    M5.Display.fillCircle(LEFT_PUPIL_X, PUPIL_Y, PUPIL_RADIUS, pupil);
    M5.Display.fillCircle(RIGHT_PUPIL_X, PUPIL_Y, PUPIL_RADIUS, pupil);
    if (state == COMPANION_FACE_ERROR) {
        M5.Display.drawLine(136, 174, 184, 154, accent);
    } else if (state == COMPANION_FACE_LISTENING) {
        M5.Display.drawCircle(160, 164, 18, accent);
        M5.Display.drawCircle(160, 164, 19, accent);
    } else if (state == COMPANION_FACE_THINKING) {
        M5.Display.fillCircle(146, 166, 4, accent);
        M5.Display.fillCircle(160, 166, 4, accent);
        M5.Display.fillCircle(174, 166, 4, accent);
    } else {
        M5.Display.fillRect(130, 156, 60, 10, accent);
        if (state == COMPANION_FACE_SPEAKING) {
            M5.Display.fillRect(140, 166, 40, 12, accent);
        }
    }
    M5.Display.fillCircle(302, 18, 8, status);
    s_pupil_offset = screensaver_motion_offset(0);
    s_screensaver_frame = 0;
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
        M5.Display.setBrightness(NORMAL_BRIGHTNESS);
        draw_face_locked(s_face_state);
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
    s_screensaver = false;
    M5.Display.setBrightness(NORMAL_BRIGHTNESS);
    draw_face_locked(state);
    xSemaphoreGive(s_board_mutex);
}

static void ui_task(void *argument)
{
    (void)argument;
    for (;;) {
        bool touched = false;
        if (take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            M5.update();
            touched = M5.Touch.getCount() > 0;
            xSemaphoreGive(s_board_mutex);
        }
        if (touched) {
            companion_hardware_mark_activity();
        }

        int64_t now = esp_timer_get_time();
        bool idle = now - last_activity_us() >=
                    (int64_t)CONFIG_STACKCHAN_SCREENSAVER_IDLE_SECONDS * 1000LL * 1000LL;
        if (take_mutex(s_board_mutex, pdMS_TO_TICKS(100))) {
            if (idle && !s_screensaver) {
                s_screensaver = true;
                s_screensaver_frame = 1;
                s_next_screensaver_frame_us = now + (int64_t)SCREENSAVER_FRAME_MS * 1000LL;
                M5.Display.setBrightness(SCREENSAVER_BRIGHTNESS);
                ESP_LOGI(TAG,
                         "Idle low-brightness pupil screensaver active; touch, voice, or reminder will wake it");
            } else if (s_screensaver && now >= s_next_screensaver_frame_us) {
                draw_screensaver_pupils_locked(screensaver_motion_offset(s_screensaver_frame));
                s_screensaver_frame++;
                s_next_screensaver_frame_us = now + (int64_t)SCREENSAVER_FRAME_MS * 1000LL;
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
    if (s_board_mutex == nullptr || s_audio_mutex == nullptr) {
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
    M5.Display.setBrightness(NORMAL_BRIGHTNESS);
    M5.Speaker.setVolume(128);
    M5.Speaker.end();
    if (!M5.Mic.begin()) {
        return ESP_FAIL;
    }

    s_last_activity_us = esp_timer_get_time();
    s_initialized = true;
    draw_face_locked(COMPANION_FACE_IDLE);
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
    audio_wav_view_t view{};
    if (!s_initialized || !audio_wav_parse(wav, wav_size, &view)) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!take_mutex(s_audio_mutex, portMAX_DELAY)) {
        return ESP_ERR_TIMEOUT;
    }

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
    while (queued && esp_timer_get_time() < deadline) {
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
    return played && microphone_restarted ? ESP_OK : ESP_FAIL;
}
