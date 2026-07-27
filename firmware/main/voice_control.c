#include "voice_control.h"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "esp_wn_iface.h"
#include "esp_wn_models.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "model_path.h"

#include "audio_wav.h"
#include "companion_hardware.h"
#include "device_transport.h"
#include "voice_protocol.h"
#include "voice_service.h"
#include "touch_interaction.h"
#include "wake_word_model.h"
#include "wake_model_ota.h"

// Audio capture and the synchronous esp_http_client/TCP path share this task.
// Physical CoreS3 testing overflowed the previous 12 KiB stack on first upload.
#define VOICE_TASK_STACK_SIZE 32768
#define VOICE_TASK_PRIORITY 6
#define VOICE_TOUCH_TASK_STACK_SIZE 4096
#define VOICE_TOUCH_TASK_PRIORITY 4
#define VOICE_TOUCH_POLL_MS 50
#define VOICE_SAMPLE_RATE 16000
#define VOICE_CAPTURE_MAX_SECONDS 8
#define VOICE_CAPTURE_MAX_SAMPLES (VOICE_SAMPLE_RATE * VOICE_CAPTURE_MAX_SECONDS)
#define VOICE_CAPTURE_WINDOW_MS 250
#define VOICE_CAPTURE_WINDOW_SAMPLES (VOICE_SAMPLE_RATE * VOICE_CAPTURE_WINDOW_MS / 1000)
#define VOICE_MIN_CAPTURE_WINDOWS 4
#define VOICE_SILENCE_WINDOWS 3
#define VOICE_DEFAULT_START_ENERGY_THRESHOLD 350
#define VOICE_DEFAULT_SILENCE_ENERGY_THRESHOLD 200
#define VOICE_START_ENERGY_THRESHOLD_MIN 100
#define VOICE_START_ENERGY_THRESHOLD_MAX 5000
#define VOICE_SILENCE_ENERGY_THRESHOLD_MIN 50
#define VOICE_SILENCE_ENERGY_THRESHOLD_MAX 4000
#define VOICE_SENSITIVE_WAKE_THRESHOLD 0.50f
#define ERROR_FACE_DURATION_MS 1500
#define NO_SPEECH_FACE_DURATION_MS 1200
#define SUCCESS_FACE_DURATION_MS 1200

static const char *TAG = "voice_control";
static SemaphoreHandle_t s_voice_session_mutex;
static bool s_started;
static TaskHandle_t s_touch_task_handle;
static portMUX_TYPE s_settings_lock = portMUX_INITIALIZER_UNLOCKED;
static portMUX_TYPE s_interaction_lock = portMUX_INITIALIZER_UNLOCKED;
static touch_interaction_phase_t s_interaction_phase = TOUCH_INTERACTION_IDLE;
static bool s_cancel_requested;
static bool s_feedback_dismiss_requested;
static bool s_press_to_talk_requested;
static bool s_press_to_talk_held;
static bool s_active_turn;
static char s_active_turn_id[DEVICE_PROTOCOL_TURN_ID_LEN];
static int64_t s_active_turn_started_us;

static void report_turn_stage(const char *turn_id,
                              int64_t started_us,
                              device_voice_turn_stage_t stage);

typedef struct {
    voice_wake_sensitivity_t wake_sensitivity;
    uint32_t speech_start_threshold;
    uint32_t speech_silence_threshold;
} voice_detection_settings_t;

typedef struct {
    esp_wn_iface_t *interface;
    model_iface_data_t *model;
    const char *name;
    int chunk_samples;
    bool used_fallback;
} active_wakenet_model_t;

static voice_detection_settings_t s_detection_settings = {
    .wake_sensitivity = VOICE_WAKE_SENSITIVITY_SENSITIVE,
    .speech_start_threshold = VOICE_DEFAULT_START_ENERGY_THRESHOLD,
    .speech_silence_threshold = VOICE_DEFAULT_SILENCE_ENERGY_THRESHOLD,
};

static touch_interaction_phase_t current_interaction_phase(void)
{
    touch_interaction_phase_t phase;
    taskENTER_CRITICAL(&s_interaction_lock);
    phase = s_interaction_phase;
    taskEXIT_CRITICAL(&s_interaction_lock);
    return phase;
}

static void set_interaction_phase(touch_interaction_phase_t phase)
{
    taskENTER_CRITICAL(&s_interaction_lock);
    s_interaction_phase = phase;
    taskEXIT_CRITICAL(&s_interaction_lock);
}

static bool cancellation_requested(void)
{
    bool requested;
    taskENTER_CRITICAL(&s_interaction_lock);
    requested = s_cancel_requested;
    taskEXIT_CRITICAL(&s_interaction_lock);
    return requested;
}

static bool press_to_talk_held(void)
{
    bool held;
    taskENTER_CRITICAL(&s_interaction_lock);
    held = s_press_to_talk_held;
    taskEXIT_CRITICAL(&s_interaction_lock);
    return held;
}

static void begin_turn(const char *turn_id, int64_t started_us)
{
    taskENTER_CRITICAL(&s_interaction_lock);
    s_cancel_requested = false;
    s_feedback_dismiss_requested = false;
    s_active_turn = true;
    s_active_turn_started_us = started_us;
    memcpy(s_active_turn_id, turn_id, sizeof(s_active_turn_id));
    taskEXIT_CRITICAL(&s_interaction_lock);
}

static void finish_turn(void)
{
    taskENTER_CRITICAL(&s_interaction_lock);
    s_active_turn = false;
    s_cancel_requested = false;
    s_active_turn_started_us = 0;
    s_press_to_talk_held = false;
    memset(s_active_turn_id, 0, sizeof(s_active_turn_id));
    taskEXIT_CRITICAL(&s_interaction_lock);
}

static bool take_press_to_talk_request(void)
{
    bool requested;
    taskENTER_CRITICAL(&s_interaction_lock);
    requested = s_press_to_talk_requested;
    s_press_to_talk_requested = false;
    taskEXIT_CRITICAL(&s_interaction_lock);
    return requested;
}

static bool take_feedback_dismiss_request(void)
{
    bool requested;
    taskENTER_CRITICAL(&s_interaction_lock);
    requested = s_feedback_dismiss_requested;
    s_feedback_dismiss_requested = false;
    taskEXIT_CRITICAL(&s_interaction_lock);
    return requested;
}

static bool online_identity_available(void)
{
    if (!device_transport_is_server_connected()) {
        return false;
    }
    device_identity_t identity = {0};
    bool available = device_identity_load(&identity) == ESP_OK && device_identity_is_valid(&identity);
    memset(&identity, 0, sizeof(identity));
    return available;
}

static void request_press_to_talk(void)
{
    taskENTER_CRITICAL(&s_interaction_lock);
    if (s_interaction_phase == TOUCH_INTERACTION_IDLE) {
        s_press_to_talk_requested = true;
        s_press_to_talk_held = true;
        s_interaction_phase = TOUCH_INTERACTION_BUSY;
    }
    taskEXIT_CRITICAL(&s_interaction_lock);
}

static void request_turn_cancellation(void)
{
    char turn_id[DEVICE_PROTOCOL_TURN_ID_LEN] = {0};
    int64_t started_us = 0;
    bool report = false;
    taskENTER_CRITICAL(&s_interaction_lock);
    if (!s_cancel_requested) {
        s_cancel_requested = true;
        if (s_active_turn) {
            memcpy(turn_id, s_active_turn_id, sizeof(turn_id));
            started_us = s_active_turn_started_us;
            report = true;
        }
    }
    taskEXIT_CRITICAL(&s_interaction_lock);

    if (report) {
        report_turn_stage(turn_id, started_us, DEVICE_VOICE_STAGE_CANCELLED);
    }
    (void)voice_service_cancel_active_turn();
    companion_hardware_request_playback_stop();
}

void voice_control_cancel_active_turn(void)
{
    request_turn_cancellation();
}

static void request_feedback_dismissal(void)
{
    taskENTER_CRITICAL(&s_interaction_lock);
    s_feedback_dismiss_requested = true;
    taskEXIT_CRITICAL(&s_interaction_lock);
}

static void voice_touch_task(void *argument)
{
    (void)argument;
    bool pressed = false;
    bool press_to_talk_started = false;
    bool long_press_evaluated = false;
    int64_t pressed_us = 0;
    for (;;) {
        companion_touch_event_t event = {0};
        bool received = companion_hardware_wait_touch_event(&event, VOICE_TOUCH_POLL_MS);
        int64_t now = received ? event.occurred_us : esp_timer_get_time();
        if (received && event.type == COMPANION_TOUCH_PRESSED) {
            pressed = true;
            press_to_talk_started = false;
            long_press_evaluated = false;
            pressed_us = event.occurred_us;
        } else if (received && event.type == COMPANION_TOUCH_RELEASED && pressed) {
            uint32_t held_ms = (uint32_t)((event.occurred_us - pressed_us) / 1000);
            taskENTER_CRITICAL(&s_interaction_lock);
            if (press_to_talk_started) {
                s_press_to_talk_held = false;
            }
            taskEXIT_CRITICAL(&s_interaction_lock);
            touch_interaction_action_t action = touch_interaction_release_action(
                current_interaction_phase(), held_ms);
            if (action == TOUCH_INTERACTION_ACTION_CANCEL) {
                request_turn_cancellation();
            } else if (action == TOUCH_INTERACTION_ACTION_DISMISS) {
                request_feedback_dismissal();
            }
            pressed = false;
            press_to_talk_started = false;
            long_press_evaluated = false;
        }

        if (pressed && !long_press_evaluated) {
            uint32_t held_ms = (uint32_t)((now - pressed_us) / 1000);
            if (held_ms >= TOUCH_INTERACTION_LONG_PRESS_MS) {
                long_press_evaluated = true;
                if (touch_interaction_should_start_press_to_talk(
                        current_interaction_phase(), held_ms, online_identity_available(), false)) {
                    request_press_to_talk();
                    press_to_talk_started = true;
                }
            }
        }
    }
}

static void create_turn_id(char turn_id[DEVICE_PROTOCOL_TURN_ID_LEN])
{
    uint8_t random_bytes[16] = {0};
    esp_fill_random(random_bytes, sizeof(random_bytes));
    random_bytes[6] = (random_bytes[6] & 0x0fU) | 0x40U;
    random_bytes[8] = (random_bytes[8] & 0x3fU) | 0x80U;
    (void)snprintf(
        turn_id,
        DEVICE_PROTOCOL_TURN_ID_LEN,
        "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
        random_bytes[0], random_bytes[1], random_bytes[2], random_bytes[3],
        random_bytes[4], random_bytes[5], random_bytes[6], random_bytes[7],
        random_bytes[8], random_bytes[9], random_bytes[10], random_bytes[11],
        random_bytes[12], random_bytes[13], random_bytes[14], random_bytes[15]);
}

static uint32_t turn_elapsed_ms(int64_t started_us)
{
    int64_t elapsed_ms = (esp_timer_get_time() - started_us) / 1000;
    if (elapsed_ms < 0) {
        return 0;
    }
    return elapsed_ms > 300000 ? 300000U : (uint32_t)elapsed_ms;
}

static void report_turn_stage(const char *turn_id,
                              int64_t started_us,
                              device_voice_turn_stage_t stage)
{
    (void)device_transport_report_voice_turn(
        stage, turn_id, turn_elapsed_ms(started_us), DEVICE_VOICE_FAILURE_NONE);
}

static void report_turn_failure(const char *turn_id,
                                int64_t started_us,
                                device_voice_turn_failure_t failure)
{
    (void)device_transport_report_voice_turn(
        DEVICE_VOICE_STAGE_FAILED, turn_id, turn_elapsed_ms(started_us), failure);
}

static void show_feedback_then_idle(companion_face_state_t feedback)
{
    uint32_t duration_ms = ERROR_FACE_DURATION_MS;
    if (feedback == COMPANION_FACE_SUCCESS) {
        duration_ms = SUCCESS_FACE_DURATION_MS;
    } else if (feedback == COMPANION_FACE_NO_SPEECH) {
        duration_ms = NO_SPEECH_FACE_DURATION_MS;
    }
    set_interaction_phase(TOUCH_INTERACTION_FEEDBACK);
    companion_hardware_set_state(feedback);
    uint32_t waited_ms = 0;
    while (waited_ms < duration_ms && !take_feedback_dismiss_request()) {
        vTaskDelay(pdMS_TO_TICKS(VOICE_TOUCH_POLL_MS));
        waited_ms += VOICE_TOUCH_POLL_MS;
    }
    companion_hardware_set_state(COMPANION_FACE_IDLE);
    set_interaction_phase(TOUCH_INTERACTION_IDLE);
}

static uint32_t mean_absolute_energy(const int16_t *samples, size_t sample_count)
{
    uint64_t total = 0;
    for (size_t index = 0; index < sample_count; ++index) {
        int32_t sample = samples[index];
        total += (uint32_t)(sample < 0 ? -sample : sample);
    }
    return sample_count == 0 ? 0 : (uint32_t)(total / sample_count);
}

static voice_detection_settings_t current_detection_settings(void)
{
    voice_detection_settings_t settings;
    taskENTER_CRITICAL(&s_settings_lock);
    settings = s_detection_settings;
    taskEXIT_CRITICAL(&s_settings_lock);
    return settings;
}

static esp_err_t capture_user_speech(int16_t *samples,
                                     size_t capacity,
                                     size_t *captured_samples,
                                     const voice_detection_settings_t *settings,
                                     uint32_t *peak_energy,
                                     bool press_to_talk)
{
    if (samples == NULL || captured_samples == NULL || settings == NULL || peak_energy == NULL ||
        capacity < VOICE_CAPTURE_WINDOW_SAMPLES) {
        return ESP_ERR_INVALID_ARG;
    }
    *captured_samples = 0;
    *peak_energy = 0;
    bool speech_started = false;
    size_t silent_windows = 0;
    while (*captured_samples + VOICE_CAPTURE_WINDOW_SAMPLES <= capacity) {
        if (cancellation_requested()) {
            return ESP_ERR_NOT_FINISHED;
        }
        if (press_to_talk && !press_to_talk_held()) {
            break;
        }
        int16_t *window = samples + *captured_samples;
        esp_err_t err = companion_hardware_record_pcm(window, VOICE_CAPTURE_WINDOW_SAMPLES,
                                                       VOICE_SAMPLE_RATE);
        if (err != ESP_OK) {
            return err;
        }
        *captured_samples += VOICE_CAPTURE_WINDOW_SAMPLES;
        if (cancellation_requested()) {
            return ESP_ERR_NOT_FINISHED;
        }
        uint32_t energy = mean_absolute_energy(window, VOICE_CAPTURE_WINDOW_SAMPLES);
        if (energy > *peak_energy) {
            *peak_energy = energy;
        }
        if (press_to_talk || (!speech_started && energy >= settings->speech_start_threshold)) {
            speech_started = true;
        }
        if (press_to_talk) {
            if (!press_to_talk_held()) {
                break;
            }
            continue;
        }
        if (speech_started && energy <= settings->speech_silence_threshold) {
            ++silent_windows;
        } else {
            silent_windows = 0;
        }
        if (speech_started && *captured_samples >=
                                  VOICE_MIN_CAPTURE_WINDOWS * VOICE_CAPTURE_WINDOW_SAMPLES &&
            silent_windows >= VOICE_SILENCE_WINDOWS) {
            *captured_samples -= (VOICE_SILENCE_WINDOWS - 1) * VOICE_CAPTURE_WINDOW_SAMPLES;
            break;
        }
    }
    return speech_started ? ESP_OK : ESP_ERR_NOT_FOUND;
}

static esp_err_t run_voice_turn(const voice_detection_settings_t *settings,
                                const char *turn_id,
                                int64_t started_us,
                                companion_face_state_t *failure_face,
                                bool press_to_talk)
{
    if (failure_face == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    *failure_face = COMPANION_FACE_RECOVERABLE_ERROR;
    device_identity_t identity = {0};
    if (!device_transport_is_wifi_connected() || device_identity_load(&identity) != ESP_OK) {
        *failure_face = COMPANION_FACE_OFFLINE;
        report_turn_failure(turn_id, started_us, DEVICE_VOICE_FAILURE_OFFLINE);
        memset(&identity, 0, sizeof(identity));
        return ESP_ERR_INVALID_STATE;
    }

    int16_t *samples = heap_caps_malloc(VOICE_CAPTURE_MAX_SAMPLES * sizeof(int16_t),
                                        MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (samples == NULL) {
        memset(&identity, 0, sizeof(identity));
        report_turn_failure(turn_id, started_us, DEVICE_VOICE_FAILURE_OUT_OF_MEMORY);
        return ESP_ERR_NO_MEM;
    }
    set_interaction_phase(TOUCH_INTERACTION_LISTENING);
    companion_hardware_set_state(COMPANION_FACE_LISTENING);
    report_turn_stage(turn_id, started_us, DEVICE_VOICE_STAGE_LISTENING);
    size_t captured_samples = 0;
    uint32_t peak_energy = 0;
    esp_err_t err = capture_user_speech(samples, VOICE_CAPTURE_MAX_SAMPLES, &captured_samples,
                                        settings, &peak_energy, press_to_talk);
    if (err != ESP_OK) {
        if (err == ESP_ERR_NOT_FOUND) {
            *failure_face = COMPANION_FACE_NO_SPEECH;
            ESP_LOGW(TAG,
                     "Speech not detected; peak_mean_energy=%lu start_threshold=%lu silence_threshold=%lu",
                     (unsigned long)peak_energy,
                     (unsigned long)settings->speech_start_threshold,
                     (unsigned long)settings->speech_silence_threshold);
        }
        heap_caps_free(samples);
        memset(&identity, 0, sizeof(identity));
        if (err == ESP_ERR_NOT_FINISHED || cancellation_requested()) {
            return ESP_ERR_NOT_FINISHED;
        }
        report_turn_failure(
            turn_id,
            started_us,
            err == ESP_ERR_NOT_FOUND ? DEVICE_VOICE_FAILURE_NO_SPEECH
                                     : DEVICE_VOICE_FAILURE_INTERNAL_ERROR);
        return err;
    }
    ESP_LOGI(TAG, "Speech captured: samples=%lu peak_mean_energy=%lu",
             (unsigned long)captured_samples, (unsigned long)peak_energy);
    report_turn_stage(turn_id, started_us, DEVICE_VOICE_STAGE_SPEECH_CAPTURED);

    size_t wav_capacity = AUDIO_WAV_HEADER_SIZE + captured_samples * sizeof(int16_t);
    uint8_t *wav = heap_caps_malloc(wav_capacity, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (wav == NULL) {
        heap_caps_free(samples);
        memset(&identity, 0, sizeof(identity));
        report_turn_failure(turn_id, started_us, DEVICE_VOICE_FAILURE_OUT_OF_MEMORY);
        return ESP_ERR_NO_MEM;
    }
    size_t wav_size = 0;
    err = audio_wav_build_pcm16_mono(wav, wav_capacity, samples, captured_samples,
                                     VOICE_SAMPLE_RATE, &wav_size);
    heap_caps_free(samples);
    if (err != ESP_OK) {
        heap_caps_free(wav);
        memset(&identity, 0, sizeof(identity));
        report_turn_failure(turn_id, started_us, DEVICE_VOICE_FAILURE_INTERNAL_ERROR);
        return err;
    }

    if (cancellation_requested()) {
        heap_caps_free(wav);
        memset(&identity, 0, sizeof(identity));
        return ESP_ERR_NOT_FINISHED;
    }
    set_interaction_phase(TOUCH_INTERACTION_PROCESSING);
    companion_hardware_set_state(COMPANION_FACE_PROCESSING);
    report_turn_stage(turn_id, started_us, DEVICE_VOICE_STAGE_UPLOAD_STARTED);
    voice_service_buffer_t response_buffer = {0};
    err = voice_service_send_turn(&identity, turn_id, wav, wav_size, &response_buffer);
    heap_caps_free(wav);
    memset(&identity, 0, sizeof(identity));
    if (cancellation_requested()) {
        voice_service_release(&response_buffer);
        return ESP_ERR_NOT_FINISHED;
    }
    if (err != ESP_OK) {
        report_turn_failure(turn_id, started_us, DEVICE_VOICE_FAILURE_UPLOAD_FAILED);
        return err;
    }

    voice_turn_response_t response = {0};
    if (!voice_protocol_parse_turn_response(response_buffer.data, response_buffer.size, &response)) {
        voice_service_release(&response_buffer);
        report_turn_failure(turn_id, started_us, DEVICE_VOICE_FAILURE_INVALID_RESPONSE);
        return ESP_ERR_INVALID_RESPONSE;
    }
    if (cancellation_requested()) {
        voice_service_release(&response_buffer);
        return ESP_ERR_NOT_FINISHED;
    }
    set_interaction_phase(TOUCH_INTERACTION_PLAYING);
    companion_hardware_set_state(COMPANION_FACE_SPEAKING);
    report_turn_stage(turn_id, started_us, DEVICE_VOICE_STAGE_PLAYBACK_STARTED);
    bool playback_cancelled = false;
    err = companion_hardware_play_wav_interruptible(
        response.wav, response.wav_size, &playback_cancelled);
    voice_service_release(&response_buffer);
    if (playback_cancelled || cancellation_requested()) {
        return ESP_ERR_NOT_FINISHED;
    }
    if (err != ESP_OK) {
        report_turn_failure(
            turn_id,
            started_us,
            err == ESP_ERR_INVALID_STATE ? DEVICE_VOICE_FAILURE_MICROPHONE_RECOVERY_FAILED
                                         : DEVICE_VOICE_FAILURE_PLAYBACK_FAILED);
        return err;
    }
    report_turn_stage(turn_id, started_us, DEVICE_VOICE_STAGE_PLAYBACK_COMPLETED);
    report_turn_stage(turn_id, started_us, DEVICE_VOICE_STAGE_LISTENING_RESUMED);
    return err;
}

static model_iface_data_t *create_wakenet_model(esp_wn_iface_t *wakenet,
                                                 const char *model_name,
                                                 voice_wake_sensitivity_t sensitivity)
{
    det_mode_t mode = sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE ? DET_MODE_95 : DET_MODE_90;
    model_iface_data_t *model = wakenet->create(model_name, mode);
    if (model == NULL) {
        return NULL;
    }
    if (sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE &&
        wakenet->set_det_threshold(model, VOICE_SENSITIVE_WAKE_THRESHOLD, 1) != 1) {
        ESP_LOGE(TAG, "WakeNet sensitive threshold configuration failed");
        wakenet->destroy(model);
        return NULL;
    }
    float threshold = wakenet->get_det_threshold(model, 1);
    ESP_LOGI(TAG, "WakeNet detection threshold configured: sensitivity=%s threshold_milli=%lu",
             sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE ? "sensitive" : "normal",
             (unsigned long)(threshold * 1000.0f + 0.5f));
    return model;
}

static void destroy_active_wakenet(active_wakenet_model_t *active)
{
    if (active != NULL && active->interface != NULL && active->model != NULL) {
        active->interface->destroy(active->model);
        active->model = NULL;
    }
}

static bool try_activate_wakenet(srmodel_list_t *models,
                                 const char *model_name,
                                 bool used_fallback,
                                 voice_wake_sensitivity_t sensitivity,
                                 active_wakenet_model_t *active)
{
    esp_wn_iface_t *wakenet = (esp_wn_iface_t *)esp_wn_handle_from_name(model_name);
    if (wakenet == NULL) {
        ESP_LOGW(TAG, "WakeNet model interface is unavailable: model=%s", model_name);
        return false;
    }
    model_iface_data_t *model = create_wakenet_model(wakenet, model_name, sensitivity);
    if (model == NULL) {
        ESP_LOGW(TAG, "WakeNet model could not be created: model=%s", model_name);
        return false;
    }

    int chunk_samples = wakenet->get_samp_chunksize(model);
    int sample_rate = wakenet->get_samp_rate(model);
    int channels = wakenet->get_channel_num(model);
    if (chunk_samples <= 0 || sample_rate != VOICE_SAMPLE_RATE || channels != 1) {
        ESP_LOGW(TAG,
                 "WakeNet model audio format is unsupported: model=%s rate=%d channels=%d chunk=%d",
                 model_name, sample_rate, channels, chunk_samples);
        wakenet->destroy(model);
        return false;
    }

    char *wake_words = esp_srmodel_get_wake_words(models, (char *)model_name);
    ESP_LOGI(TAG, "WakeNet model selected: model=%s source=%s wake_words=%s",
             model_name, used_fallback ? "fallback" : "configured",
             wake_words == NULL ? "unavailable" : wake_words);
    free(wake_words);
    active->interface = wakenet;
    active->model = model;
    active->name = model_name;
    active->chunk_samples = chunk_samples;
    active->used_fallback = used_fallback;
    return true;
}

static bool activate_configured_wakenet(srmodel_list_t *models,
                                        const char *configured_model_name,
                                        voice_wake_sensitivity_t sensitivity,
                                        active_wakenet_model_t *active)
{
    if (models == NULL || models->num <= 0 || configured_model_name == NULL || active == NULL) {
        return false;
    }
    memset(active, 0, sizeof(*active));
    const char *const *model_names = (const char *const *)models->model_name;
    wake_word_model_selection_t selection = wake_word_model_select(
        model_names,
        (size_t)models->num,
        configured_model_name,
        WAKE_WORD_DEFAULT_MODEL_NAME);
    if (selection.name == NULL) {
        ESP_LOGE(TAG, "Neither the configured nor fallback WakeNet model is packaged");
        return false;
    }
    if (selection.used_fallback) {
        ESP_LOGW(TAG, "Configured WakeNet model is unavailable; using fallback: configured=%s fallback=%s",
                 configured_model_name, selection.name);
    }
    if (try_activate_wakenet(models, selection.name, selection.used_fallback, sensitivity, active)) {
        return true;
    }

    const char *fallback = wake_word_model_find(
        model_names, (size_t)models->num, WAKE_WORD_DEFAULT_MODEL_NAME);
    if (selection.used_fallback || fallback == NULL || strcmp(selection.name, fallback) == 0) {
        return false;
    }
    ESP_LOGW(TAG, "Configured WakeNet model failed validation; using fallback: configured=%s fallback=%s",
             selection.name, fallback);
    return try_activate_wakenet(models, fallback, true, sensitivity, active);
}

static void execute_voice_turn(const voice_detection_settings_t *settings,
                               device_voice_turn_stage_t trigger_stage,
                               bool press_to_talk)
{
    char turn_id[DEVICE_PROTOCOL_TURN_ID_LEN] = {0};
    create_turn_id(turn_id);
    int64_t turn_started_us = esp_timer_get_time();
    if (xSemaphoreTake(s_voice_session_mutex, portMAX_DELAY) != pdTRUE) {
        set_interaction_phase(TOUCH_INTERACTION_IDLE);
        return;
    }

    begin_turn(turn_id, turn_started_us);
    report_turn_stage(turn_id, turn_started_us, trigger_stage);
    companion_face_state_t failure_face = COMPANION_FACE_RECOVERABLE_ERROR;
    esp_err_t err = run_voice_turn(
        settings, turn_id, turn_started_us, &failure_face, press_to_talk);
    bool cancelled = err == ESP_ERR_NOT_FINISHED || cancellation_requested();
    finish_turn();
    if (cancelled) {
        ESP_LOGI(TAG, "Voice turn cancelled by touch");
        companion_hardware_set_state(COMPANION_FACE_IDLE);
        set_interaction_phase(TOUCH_INTERACTION_IDLE);
    } else if (err == ESP_OK) {
        show_feedback_then_idle(COMPANION_FACE_SUCCESS);
    } else {
        ESP_LOGW(TAG, "Voice turn failed safely: %s", esp_err_to_name(err));
        show_feedback_then_idle(failure_face);
    }
    xSemaphoreGive(s_voice_session_mutex);
}

static void voice_task(void *argument)
{
    (void)argument;
    const char *partition_label = wake_model_ota_active_partition_label();
    const char *configured_model_name = wake_model_ota_active_model_name();
    srmodel_list_t *models = esp_srmodel_init(partition_label);
    voice_detection_settings_t active_settings = current_detection_settings();
    active_wakenet_model_t active = {0};
    if (!activate_configured_wakenet(
            models, configured_model_name, active_settings.wake_sensitivity, &active)) {
        ESP_LOGE(TAG, "WakeNet did not initialize with a valid configured or fallback model");
        if (models != NULL) {
            esp_srmodel_deinit(models);
        }
        if (wake_model_ota_is_pending()) {
            wake_model_ota_rollback_and_restart();
        }
        vTaskDelete(NULL);
        return;
    }

    size_t chunk_capacity = (size_t)active.chunk_samples;
    int16_t *chunk = heap_caps_malloc(chunk_capacity * sizeof(int16_t),
                                      MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT);
    if (chunk == NULL) {
        destroy_active_wakenet(&active);
        esp_srmodel_deinit(models);
        if (wake_model_ota_is_pending()) {
            wake_model_ota_rollback_and_restart();
        }
        vTaskDelete(NULL);
        return;
    }

    if (wake_model_ota_is_pending()) {
        if (active.used_fallback || wake_model_ota_confirm_active() != ESP_OK) {
            destroy_active_wakenet(&active);
            heap_caps_free(chunk);
            esp_srmodel_deinit(models);
            wake_model_ota_rollback_and_restart();
        }
    }

    ESP_LOGI(TAG, "WakeNet listening: model=%s sensitivity=%s",
             active.name,
             active_settings.wake_sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE
                 ? "sensitive"
                 : "normal");
    for (;;) {
        if (take_press_to_talk_request()) {
            active_settings = current_detection_settings();
            execute_voice_turn(
                &active_settings, DEVICE_VOICE_STAGE_TOUCH_STARTED, true);
            destroy_active_wakenet(&active);
            continue;
        }
        voice_detection_settings_t desired_settings = current_detection_settings();
        if (active.model == NULL || desired_settings.wake_sensitivity != active_settings.wake_sensitivity) {
            destroy_active_wakenet(&active);
            active_wakenet_model_t replacement = {0};
            if (!activate_configured_wakenet(
                    models, configured_model_name, desired_settings.wake_sensitivity, &replacement)) {
                ESP_LOGE(TAG, "WakeNet model activation failed; retrying");
                vTaskDelay(pdMS_TO_TICKS(1000));
                continue;
            }
            size_t required_capacity = (size_t)replacement.chunk_samples;
            if (required_capacity != chunk_capacity) {
                int16_t *replacement_chunk = heap_caps_malloc(
                    required_capacity * sizeof(int16_t), MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT);
                if (replacement_chunk == NULL) {
                    destroy_active_wakenet(&replacement);
                    ESP_LOGE(TAG, "WakeNet capture buffer resize failed; retrying");
                    vTaskDelay(pdMS_TO_TICKS(1000));
                    continue;
                }
                heap_caps_free(chunk);
                chunk = replacement_chunk;
                chunk_capacity = required_capacity;
            }
            active = replacement;
            active_settings = desired_settings;
            ESP_LOGI(TAG, "WakeNet listening resumed: model=%s sensitivity=%s",
                     active.name,
                     active_settings.wake_sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE
                         ? "sensitive"
                         : "normal");
        }
        esp_err_t err = companion_hardware_record_pcm(
            chunk, (size_t)active.chunk_samples, VOICE_SAMPLE_RATE);
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "WakeNet microphone capture failed: %s", esp_err_to_name(err));
            vTaskDelay(pdMS_TO_TICKS(250));
            continue;
        }
        if (active.interface->detect(active.model, chunk) != WAKENET_DETECTED) {
            continue;
        }

        companion_hardware_mark_activity();
        active_settings = current_detection_settings();
        execute_voice_turn(
            &active_settings, DEVICE_VOICE_STAGE_WAKE_DETECTED, false);
        destroy_active_wakenet(&active);
    }
}

esp_err_t voice_control_start(void)
{
    if (s_started) {
        return ESP_ERR_INVALID_STATE;
    }
    s_voice_session_mutex = xSemaphoreCreateMutex();
    if (s_voice_session_mutex == NULL) {
        return ESP_ERR_NO_MEM;
    }
    esp_err_t service_err = voice_service_init();
    if (service_err != ESP_OK) {
        vSemaphoreDelete(s_voice_session_mutex);
        s_voice_session_mutex = NULL;
        return service_err;
    }
    if (xTaskCreate(voice_touch_task,
                    "voice_touch",
                    VOICE_TOUCH_TASK_STACK_SIZE,
                    NULL,
                    VOICE_TOUCH_TASK_PRIORITY,
                    &s_touch_task_handle) != pdPASS) {
        vSemaphoreDelete(s_voice_session_mutex);
        s_voice_session_mutex = NULL;
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(voice_task, "voice_control", VOICE_TASK_STACK_SIZE, NULL,
                    VOICE_TASK_PRIORITY, NULL) != pdPASS) {
        vTaskDelete(s_touch_task_handle);
        s_touch_task_handle = NULL;
        vSemaphoreDelete(s_voice_session_mutex);
        s_voice_session_mutex = NULL;
        return ESP_ERR_NO_MEM;
    }
    s_started = true;
    return ESP_OK;
}

esp_err_t voice_control_configure(voice_wake_sensitivity_t wake_sensitivity,
                                  uint32_t speech_start_threshold,
                                  uint32_t speech_silence_threshold)
{
    if ((wake_sensitivity != VOICE_WAKE_SENSITIVITY_NORMAL &&
         wake_sensitivity != VOICE_WAKE_SENSITIVITY_SENSITIVE) ||
        speech_start_threshold < VOICE_START_ENERGY_THRESHOLD_MIN ||
        speech_start_threshold > VOICE_START_ENERGY_THRESHOLD_MAX ||
        speech_silence_threshold < VOICE_SILENCE_ENERGY_THRESHOLD_MIN ||
        speech_silence_threshold > VOICE_SILENCE_ENERGY_THRESHOLD_MAX ||
        speech_silence_threshold >= speech_start_threshold) {
        return ESP_ERR_INVALID_ARG;
    }

    taskENTER_CRITICAL(&s_settings_lock);
    s_detection_settings.wake_sensitivity = wake_sensitivity;
    s_detection_settings.speech_start_threshold = speech_start_threshold;
    s_detection_settings.speech_silence_threshold = speech_silence_threshold;
    taskEXIT_CRITICAL(&s_settings_lock);
    ESP_LOGI(TAG, "Voice detection configured: sensitivity=%s start_threshold=%lu silence_threshold=%lu",
             wake_sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE ? "sensitive" : "normal",
             (unsigned long)speech_start_threshold,
             (unsigned long)speech_silence_threshold);
    return ESP_OK;
}

esp_err_t voice_control_play_reminder(const device_identity_t *identity,
                                      const char *reminder_id,
                                      bool *cancelled)
{
    if (!s_started || !device_identity_is_valid(identity) || reminder_id == NULL ||
        cancelled == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    *cancelled = false;
    if (xSemaphoreTake(s_voice_session_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_TIMEOUT;
    }

    taskENTER_CRITICAL(&s_interaction_lock);
    s_cancel_requested = false;
    s_feedback_dismiss_requested = false;
    s_active_turn = false;
    s_interaction_phase = TOUCH_INTERACTION_BUSY;
    taskEXIT_CRITICAL(&s_interaction_lock);
    companion_hardware_mark_activity();
    companion_hardware_set_state(COMPANION_FACE_PROCESSING);
    voice_service_buffer_t audio = {0};
    esp_err_t err = voice_service_fetch_reminder(identity, reminder_id, &audio);
    if (err == ESP_OK) {
        set_interaction_phase(TOUCH_INTERACTION_PLAYING);
        companion_hardware_set_state(COMPANION_FACE_SPEAKING);
        err = companion_hardware_play_wav_interruptible(audio.data, audio.size, cancelled);
    }
    voice_service_release(&audio);
    if (*cancelled) {
        companion_hardware_set_state(COMPANION_FACE_IDLE);
        set_interaction_phase(TOUCH_INTERACTION_IDLE);
    } else if (err == ESP_OK) {
        show_feedback_then_idle(COMPANION_FACE_SUCCESS);
    } else {
        ESP_LOGW(TAG, "Reminder playback failed safely: %s", esp_err_to_name(err));
        show_feedback_then_idle(COMPANION_FACE_RECOVERABLE_ERROR);
    }
    taskENTER_CRITICAL(&s_interaction_lock);
    s_cancel_requested = false;
    taskEXIT_CRITICAL(&s_interaction_lock);
    xSemaphoreGive(s_voice_session_mutex);
    return err;
}
