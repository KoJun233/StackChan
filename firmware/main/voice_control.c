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

static void voice_task(void *argument)
{
    (void)argument;
    srmodel_list_t *models = esp_srmodel_init("model");
    char *model_name = models == NULL ? NULL : esp_srmodel_filter(models, ESP_WN_PREFIX, "histackchan");
    esp_wn_iface_t *wakenet = model_name == NULL ? NULL : (esp_wn_iface_t *)esp_wn_handle_from_name(model_name);
    voice_detection_settings_t active_settings = current_detection_settings();
    model_iface_data_t *model = wakenet == NULL
                                    ? NULL
                                    : create_wakenet_model(wakenet, model_name,
                                                           active_settings.wake_sensitivity);
    if (model == NULL) {
        ESP_LOGE(TAG, "WakeNet Hi, Stack Chan model did not initialize");
        if (models != NULL) {
            esp_srmodel_deinit(models);
        }
        vTaskDelete(NULL);
        return;
    }

    int chunk_samples = wakenet->get_samp_chunksize(model);
    int sample_rate = wakenet->get_samp_rate(model);
    int channels = wakenet->get_channel_num(model);
    if (chunk_samples <= 0 || sample_rate != VOICE_SAMPLE_RATE || channels != 1) {
        ESP_LOGE(TAG, "WakeNet model audio format is unsupported");
        wakenet->destroy(model);
        esp_srmodel_deinit(models);
        vTaskDelete(NULL);
        return;
    }
    int16_t *chunk = heap_caps_malloc((size_t)chunk_samples * sizeof(int16_t),
                                      MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT);
    if (chunk == NULL) {
        wakenet->destroy(model);
        esp_srmodel_deinit(models);
        vTaskDelete(NULL);
        return;
    }

    ESP_LOGI(TAG, "WakeNet listening for Hi, Stack Chan with %s sensitivity",
             active_settings.wake_sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE
                 ? "sensitive"
                 : "normal");
    for (;;) {
        voice_detection_settings_t desired_settings = current_detection_settings();
        if (model == NULL || desired_settings.wake_sensitivity != active_settings.wake_sensitivity) {
            if (model != NULL) {
                wakenet->destroy(model);
            }
            model = create_wakenet_model(wakenet, model_name, desired_settings.wake_sensitivity);
            if (model == NULL) {
                ESP_LOGE(TAG, "WakeNet model recreation failed; retrying");
                vTaskDelay(pdMS_TO_TICKS(1000));
                continue;
            }
            if (wakenet->get_samp_chunksize(model) != chunk_samples ||
                wakenet->get_samp_rate(model) != VOICE_SAMPLE_RATE ||
                wakenet->get_channel_num(model) != 1) {
                ESP_LOGE(TAG, "Recreated WakeNet model audio format is unsupported");
                wakenet->destroy(model);
                model = NULL;
                vTaskDelay(pdMS_TO_TICKS(1000));
                continue;
            }
            active_settings = desired_settings;
            ESP_LOGI(TAG, "WakeNet listening resumed with %s sensitivity",
                     active_settings.wake_sensitivity == VOICE_WAKE_SENSITIVITY_SENSITIVE
                         ? "sensitive"
                         : "normal");
        }
        esp_err_t err = companion_hardware_record_pcm(chunk, (size_t)chunk_samples, VOICE_SAMPLE_RATE);
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "WakeNet microphone capture failed: %s", esp_err_to_name(err));
            vTaskDelay(pdMS_TO_TICKS(250));
            continue;
        }
        if (wakenet->detect(model, chunk) != WAKENET_DETECTED) {
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
        wakenet->destroy(model);
        model = NULL;
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
