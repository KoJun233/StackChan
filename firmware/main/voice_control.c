#include "voice_control.h"

#include <stdlib.h>
#include <string.h>

#include "esp_heap_caps.h"
#include "esp_log.h"
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
#include "wake_word_model.h"
#include "wake_model_ota.h"

#define VOICE_TASK_STACK_SIZE 12288
#define VOICE_TASK_PRIORITY 6
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

static const char *TAG = "voice_control";
static SemaphoreHandle_t s_voice_session_mutex;
static bool s_started;
static portMUX_TYPE s_settings_lock = portMUX_INITIALIZER_UNLOCKED;

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

static void show_error_then_idle(void)
{
    companion_hardware_set_state(COMPANION_FACE_ERROR);
    vTaskDelay(pdMS_TO_TICKS(ERROR_FACE_DURATION_MS));
    companion_hardware_set_state(COMPANION_FACE_IDLE);
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
                                     uint32_t *peak_energy)
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
        int16_t *window = samples + *captured_samples;
        esp_err_t err = companion_hardware_record_pcm(window, VOICE_CAPTURE_WINDOW_SAMPLES,
                                                       VOICE_SAMPLE_RATE);
        if (err != ESP_OK) {
            return err;
        }
        *captured_samples += VOICE_CAPTURE_WINDOW_SAMPLES;
        uint32_t energy = mean_absolute_energy(window, VOICE_CAPTURE_WINDOW_SAMPLES);
        if (energy > *peak_energy) {
            *peak_energy = energy;
        }
        if (!speech_started && energy >= settings->speech_start_threshold) {
            speech_started = true;
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

static esp_err_t run_voice_turn(const voice_detection_settings_t *settings)
{
    device_identity_t identity = {0};
    if (!device_transport_is_wifi_connected() || device_identity_load(&identity) != ESP_OK) {
        return ESP_ERR_INVALID_STATE;
    }

    int16_t *samples = heap_caps_malloc(VOICE_CAPTURE_MAX_SAMPLES * sizeof(int16_t),
                                        MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (samples == NULL) {
        return ESP_ERR_NO_MEM;
    }
    companion_hardware_set_state(COMPANION_FACE_LISTENING);
    size_t captured_samples = 0;
    uint32_t peak_energy = 0;
    esp_err_t err = capture_user_speech(samples, VOICE_CAPTURE_MAX_SAMPLES, &captured_samples,
                                        settings, &peak_energy);
    if (err != ESP_OK) {
        if (err == ESP_ERR_NOT_FOUND) {
            ESP_LOGW(TAG,
                     "Speech not detected; peak_mean_energy=%lu start_threshold=%lu silence_threshold=%lu",
                     (unsigned long)peak_energy,
                     (unsigned long)settings->speech_start_threshold,
                     (unsigned long)settings->speech_silence_threshold);
        }
        heap_caps_free(samples);
        return err;
    }
    ESP_LOGI(TAG, "Speech captured: samples=%lu peak_mean_energy=%lu",
             (unsigned long)captured_samples, (unsigned long)peak_energy);

    size_t wav_capacity = AUDIO_WAV_HEADER_SIZE + captured_samples * sizeof(int16_t);
    uint8_t *wav = heap_caps_malloc(wav_capacity, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (wav == NULL) {
        heap_caps_free(samples);
        return ESP_ERR_NO_MEM;
    }
    size_t wav_size = 0;
    err = audio_wav_build_pcm16_mono(wav, wav_capacity, samples, captured_samples,
                                     VOICE_SAMPLE_RATE, &wav_size);
    heap_caps_free(samples);
    if (err != ESP_OK) {
        heap_caps_free(wav);
        return err;
    }

    companion_hardware_set_state(COMPANION_FACE_THINKING);
    voice_service_buffer_t response_buffer = {0};
    err = voice_service_send_turn(&identity, wav, wav_size, &response_buffer);
    heap_caps_free(wav);
    memset(&identity, 0, sizeof(identity));
    if (err != ESP_OK) {
        return err;
    }

    voice_turn_response_t response = {0};
    if (!voice_protocol_parse_turn_response(response_buffer.data, response_buffer.size, &response)) {
        voice_service_release(&response_buffer);
        return ESP_ERR_INVALID_RESPONSE;
    }
    companion_hardware_set_state(COMPANION_FACE_SPEAKING);
    err = companion_hardware_play_wav(response.wav, response.wav_size);
    voice_service_release(&response_buffer);
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
        if (xSemaphoreTake(s_voice_session_mutex, portMAX_DELAY) == pdTRUE) {
            active_settings = current_detection_settings();
            err = run_voice_turn(&active_settings);
            if (err == ESP_OK) {
                companion_hardware_set_state(COMPANION_FACE_IDLE);
            } else {
                ESP_LOGW(TAG, "Voice turn failed safely: %s", esp_err_to_name(err));
                show_error_then_idle();
            }
            xSemaphoreGive(s_voice_session_mutex);
        }
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
    if (xTaskCreate(voice_task, "voice_control", VOICE_TASK_STACK_SIZE, NULL,
                    VOICE_TASK_PRIORITY, NULL) != pdPASS) {
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

esp_err_t voice_control_play_reminder(const device_identity_t *identity, const char *reminder_id)
{
    if (!s_started || !device_identity_is_valid(identity) || reminder_id == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    if (xSemaphoreTake(s_voice_session_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_TIMEOUT;
    }

    companion_hardware_mark_activity();
    companion_hardware_set_state(COMPANION_FACE_THINKING);
    voice_service_buffer_t audio = {0};
    esp_err_t err = voice_service_fetch_reminder(identity, reminder_id, &audio);
    if (err == ESP_OK) {
        companion_hardware_set_state(COMPANION_FACE_SPEAKING);
        err = companion_hardware_play_wav(audio.data, audio.size);
    }
    voice_service_release(&audio);
    if (err == ESP_OK) {
        companion_hardware_set_state(COMPANION_FACE_IDLE);
    } else {
        ESP_LOGW(TAG, "Reminder playback failed safely: %s", esp_err_to_name(err));
        show_error_then_idle();
    }
    xSemaphoreGive(s_voice_session_mutex);
    return err;
}
