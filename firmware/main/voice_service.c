#include "voice_service.h"

#include <stdbool.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#include "esp_heap_caps.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "audio_wav.h"
#include "device_endpoint.h"
#include "voice_protocol.h"

#define VOICE_SERVICE_MAX_RESPONSE_SIZE (2U * 1024U * 1024U)
#define VOICE_SERVICE_INITIAL_CAPACITY 8192U
#define VOICE_SERVICE_TIMEOUT_MS 90000
#define VOICE_SERVICE_UPLOAD_TIMEOUT_MS 15000
#define VOICE_SERVICE_HTTP_BUFFER_SIZE 1024U
#define VOICE_SERVICE_UPLOAD_CHUNK_SIZE 1024U
#define VOICE_SERVICE_UPLOAD_LOG_INTERVAL (16U * 1024U)

static const char *TAG = "voice_service";

typedef struct {
    uint8_t *data;
    size_t size;
    size_t capacity;
    bool failed;
} response_accumulator_t;

typedef struct {
    response_accumulator_t legacy;
    uint8_t prefix[4];
    size_t prefix_size;
    uint8_t frame_header[5];
    size_t frame_header_size;
    uint8_t *frame_payload;
    size_t frame_payload_size;
    size_t frame_payload_received;
    bool streamed;
    bool terminal;
    bool failed;
    voice_service_stream_frame_handler_t frame_handler;
    void *frame_context;
} streaming_response_t;

struct voice_service_live_upload {
    esp_http_client_handle_t client;
    streaming_response_t response;
    wifi_ps_type_t previous_power_save;
    int64_t started_us;
    int64_t capture_finished_us;
    size_t pcm_bytes_sent;
    bool restore_power_save;
    bool active_client;
};

static SemaphoreHandle_t s_active_client_mutex;
static esp_http_client_handle_t s_active_turn_client;

esp_err_t voice_service_init(void)
{
    if (s_active_client_mutex != NULL) {
        return ESP_OK;
    }
    s_active_client_mutex = xSemaphoreCreateMutex();
    return s_active_client_mutex == NULL ? ESP_ERR_NO_MEM : ESP_OK;
}

static esp_err_t set_active_turn_client(esp_http_client_handle_t client)
{
    if (s_active_client_mutex == NULL ||
        xSemaphoreTake(s_active_client_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    s_active_turn_client = client;
    xSemaphoreGive(s_active_client_mutex);
    return ESP_OK;
}

static void clear_active_turn_client(esp_http_client_handle_t client)
{
    if (s_active_client_mutex == NULL ||
        xSemaphoreTake(s_active_client_mutex, portMAX_DELAY) != pdTRUE) {
        return;
    }
    if (s_active_turn_client == client) {
        s_active_turn_client = NULL;
    }
    xSemaphoreGive(s_active_client_mutex);
}

esp_err_t voice_service_cancel_active_turn(void)
{
    if (s_active_client_mutex == NULL ||
        xSemaphoreTake(s_active_client_mutex, pdMS_TO_TICKS(500)) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    esp_err_t err = s_active_turn_client == NULL
                        ? ESP_ERR_NOT_FOUND
                        : esp_http_client_cancel_request(s_active_turn_client);
    xSemaphoreGive(s_active_client_mutex);
    return err;
}

static bool reserve_response(response_accumulator_t *response, size_t required)
{
    if (required > VOICE_SERVICE_MAX_RESPONSE_SIZE) {
        return false;
    }
    if (required <= response->capacity) {
        return true;
    }
    size_t capacity = response->capacity == 0 ? VOICE_SERVICE_INITIAL_CAPACITY : response->capacity;
    while (capacity < required) {
        if (capacity > VOICE_SERVICE_MAX_RESPONSE_SIZE / 2) {
            capacity = VOICE_SERVICE_MAX_RESPONSE_SIZE;
        } else {
            capacity *= 2;
        }
    }
    uint8_t *data = heap_caps_realloc(response->data, capacity, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (data == NULL) {
        return false;
    }
    response->data = data;
    response->capacity = capacity;
    return true;
}

static esp_err_t response_event_handler(esp_http_client_event_t *event)
{
    response_accumulator_t *response = event == NULL ? NULL : event->user_data;
    if (response == NULL || response->failed) {
        return ESP_FAIL;
    }
    if (event->event_id != HTTP_EVENT_ON_DATA || event->data_len <= 0) {
        return ESP_OK;
    }
    size_t data_size = (size_t)event->data_len;
    if (data_size > VOICE_SERVICE_MAX_RESPONSE_SIZE - response->size ||
        !reserve_response(response, response->size + data_size)) {
        response->failed = true;
        return ESP_ERR_NO_MEM;
    }
    memcpy(response->data + response->size, event->data, data_size);
    response->size += data_size;
    return ESP_OK;
}

static uint32_t read_be32(const uint8_t *value)
{
    return ((uint32_t)value[0] << 24) |
           ((uint32_t)value[1] << 16) |
           ((uint32_t)value[2] << 8) |
           (uint32_t)value[3];
}

static bool stream_frame_size_valid(uint8_t type, size_t payload_size)
{
    if (type == VOICE_STREAM_FRAME_START || type == VOICE_STREAM_FRAME_COMPLETE ||
        type == VOICE_STREAM_FRAME_ERROR) {
        return payload_size > 0 && payload_size <= 8192U;
    }
    return type == VOICE_STREAM_FRAME_AUDIO && payload_size >= sizeof(uint32_t) + 44U &&
           payload_size <= sizeof(uint32_t) + VOICE_PROTOCOL_STREAM_MAX_AUDIO_LEN;
}

static esp_err_t append_legacy(response_accumulator_t *response,
                               const uint8_t *data,
                               size_t data_size)
{
    if (data_size > VOICE_SERVICE_MAX_RESPONSE_SIZE - response->size ||
        !reserve_response(response, response->size + data_size)) {
        response->failed = true;
        return ESP_ERR_NO_MEM;
    }
    memcpy(response->data + response->size, data, data_size);
    response->size += data_size;
    return ESP_OK;
}

static esp_err_t consume_streaming_data(streaming_response_t *response,
                                        const uint8_t *data,
                                        size_t data_size)
{
    while (data_size > 0) {
        if (response->prefix_size < sizeof(response->prefix)) {
            size_t needed = sizeof(response->prefix) - response->prefix_size;
            size_t copied = data_size < needed ? data_size : needed;
            memcpy(response->prefix + response->prefix_size, data, copied);
            response->prefix_size += copied;
            data += copied;
            data_size -= copied;
            if (response->prefix_size < sizeof(response->prefix)) return ESP_OK;
            if (memcmp(response->prefix, "SCV2", sizeof(response->prefix)) == 0) {
                response->streamed = true;
            } else if (memcmp(response->prefix, "SCV1", sizeof(response->prefix)) == 0) {
                esp_err_t err = append_legacy(&response->legacy, response->prefix, sizeof(response->prefix));
                if (err != ESP_OK) return err;
            } else {
                return ESP_ERR_INVALID_RESPONSE;
            }
        }

        if (!response->streamed) return append_legacy(&response->legacy, data, data_size);
        if (response->terminal) return ESP_ERR_INVALID_RESPONSE;

        if (response->frame_header_size < sizeof(response->frame_header)) {
            size_t needed = sizeof(response->frame_header) - response->frame_header_size;
            size_t copied = data_size < needed ? data_size : needed;
            memcpy(response->frame_header + response->frame_header_size, data, copied);
            response->frame_header_size += copied;
            data += copied;
            data_size -= copied;
            if (response->frame_header_size < sizeof(response->frame_header)) return ESP_OK;
            response->frame_payload_size = read_be32(response->frame_header + 1);
            if (!stream_frame_size_valid(response->frame_header[0], response->frame_payload_size)) {
                return ESP_ERR_INVALID_RESPONSE;
            }
            response->frame_payload = heap_caps_malloc(
                response->frame_payload_size, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
            if (response->frame_payload == NULL) return ESP_ERR_NO_MEM;
        }

        size_t needed = response->frame_payload_size - response->frame_payload_received;
        size_t copied = data_size < needed ? data_size : needed;
        memcpy(response->frame_payload + response->frame_payload_received, data, copied);
        response->frame_payload_received += copied;
        data += copied;
        data_size -= copied;
        if (response->frame_payload_received == response->frame_payload_size) {
            uint8_t type = response->frame_header[0];
            bool retain_payload = false;
            esp_err_t err = response->frame_handler(
                type,
                response->frame_payload,
                response->frame_payload_size,
                &retain_payload,
                response->frame_context);
            // Retained frames are released by the consumer after asynchronous use.
            if (!retain_payload) {
                heap_caps_free(response->frame_payload);
            }
            response->frame_payload = NULL;
            response->frame_payload_size = 0;
            response->frame_payload_received = 0;
            response->frame_header_size = 0;
            if (err != ESP_OK) return err;
            if (type == VOICE_STREAM_FRAME_COMPLETE || type == VOICE_STREAM_FRAME_ERROR) {
                response->terminal = true;
            }
        }
    }
    return ESP_OK;
}

static esp_err_t streaming_response_event_handler(esp_http_client_event_t *event)
{
    streaming_response_t *response = event == NULL ? NULL : event->user_data;
    if (response == NULL || response->failed) return ESP_FAIL;
    if (event->event_id != HTTP_EVENT_ON_DATA || event->data_len <= 0) return ESP_OK;
    esp_err_t err = consume_streaming_data(
        response, (const uint8_t *)event->data, (size_t)event->data_len);
    if (err != ESP_OK) response->failed = true;
    return err;
}

static void log_upload_state(const char *stage,
                             size_t sent,
                             size_t total,
                             int64_t started_us)
{
    ESP_LOGI(TAG,
             "Voice upload %s: sent=%u/%u elapsed_ms=%llu internal_free=%u "
             "internal_largest=%u internal_minimum=%u psram_free=%u",
             stage,
             (unsigned)sent,
             (unsigned)total,
             (unsigned long long)((esp_timer_get_time() - started_us) / 1000),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_minimum_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
}

static esp_err_t read_http_response(esp_http_client_handle_t client)
{
    int64_t content_length = esp_http_client_fetch_headers(client);
    if (content_length < 0) {
        return content_length == -ESP_ERR_HTTP_EAGAIN
                   ? ESP_ERR_HTTP_EAGAIN
                   : ESP_ERR_HTTP_FETCH_HEADER;
    }

    return esp_http_client_is_complete_data_received(client)
               ? ESP_OK
               : esp_http_client_flush_response(client, NULL);
}

static esp_err_t write_bounded(esp_http_client_handle_t client,
                               const uint8_t *data,
                               size_t data_size)
{
    if (client == NULL || (data == NULL && data_size > 0)) {
        return ESP_ERR_INVALID_ARG;
    }
    size_t sent = 0;
    while (sent < data_size) {
        size_t remaining = data_size - sent;
        size_t write_size = remaining < VOICE_SERVICE_UPLOAD_CHUNK_SIZE
                                ? remaining
                                : VOICE_SERVICE_UPLOAD_CHUNK_SIZE;
        int written = esp_http_client_write(
            client, (const char *)data + sent, (int)write_size);
        if (written <= 0) {
            return ESP_ERR_HTTP_WRITE_DATA;
        }
        sent += (size_t)written;
    }
    return ESP_OK;
}

static esp_err_t write_transfer_chunk(esp_http_client_handle_t client,
                                      const uint8_t *data,
                                      size_t data_size)
{
    if (data == NULL || data_size == 0 || data_size > UINT32_MAX) {
        return ESP_ERR_INVALID_ARG;
    }
    char prefix[16] = {0};
    int prefix_size = snprintf(prefix, sizeof(prefix), "%x\r\n", (unsigned)data_size);
    if (prefix_size <= 0 || (size_t)prefix_size >= sizeof(prefix)) {
        return ESP_ERR_INVALID_SIZE;
    }
    esp_err_t err = write_bounded(client, (const uint8_t *)prefix, (size_t)prefix_size);
    if (err == ESP_OK) {
        err = write_bounded(client, data, data_size);
    }
    if (err == ESP_OK) {
        static const uint8_t suffix[] = "\r\n";
        err = write_bounded(client, suffix, sizeof(suffix) - 1);
    }
    memset(prefix, 0, sizeof(prefix));
    return err;
}

static void restore_live_upload_power_save(voice_service_live_upload_t *upload)
{
    if (upload == NULL || !upload->restore_power_save) {
        return;
    }
    esp_err_t err = esp_wifi_set_ps(upload->previous_power_save);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Could not restore Wi-Fi power save after live voice upload: %s",
                 esp_err_to_name(err));
    }
    upload->restore_power_save = false;
}

static void destroy_live_upload(voice_service_live_upload_t *upload, bool keep_legacy)
{
    if (upload == NULL) {
        return;
    }
    restore_live_upload_power_save(upload);
    if (upload->active_client) {
        clear_active_turn_client(upload->client);
        upload->active_client = false;
    }
    if (upload->client != NULL) {
        (void)esp_http_client_cleanup(upload->client);
        upload->client = NULL;
    }
    heap_caps_free(upload->response.frame_payload);
    if (!keep_legacy) {
        heap_caps_free(upload->response.legacy.data);
    }
    heap_caps_free(upload);
}

static esp_err_t perform_streaming_post(esp_http_client_handle_t client,
                                        const uint8_t *request_body,
                                        size_t request_size)
{
    if (client == NULL || request_body == NULL || request_size == 0 || request_size > INT_MAX) {
        return ESP_ERR_INVALID_ARG;
    }

    int64_t started_us = esp_timer_get_time();
    size_t sent = 0;
    size_t next_log = VOICE_SERVICE_UPLOAD_LOG_INTERVAL;
    wifi_ps_type_t previous_power_save = WIFI_PS_NONE;
    bool restore_power_save = esp_wifi_get_ps(&previous_power_save) == ESP_OK &&
                              previous_power_save != WIFI_PS_NONE;
    if (restore_power_save) {
        esp_err_t power_err = esp_wifi_set_ps(WIFI_PS_NONE);
        if (power_err != ESP_OK) {
            ESP_LOGW(TAG, "Could not suspend Wi-Fi power save for voice upload: %s",
                     esp_err_to_name(power_err));
            restore_power_save = false;
        }
    }

    log_upload_state("started", sent, request_size, started_us);
    esp_err_t err = esp_http_client_set_timeout_ms(client, VOICE_SERVICE_UPLOAD_TIMEOUT_MS);
    if (err == ESP_OK) {
        err = esp_http_client_open(client, (int)request_size);
        if (err == ESP_OK) {
            log_upload_state("opened", sent, request_size, started_us);
        }
    }
    while (err == ESP_OK && sent < request_size) {
        size_t remaining = request_size - sent;
        size_t chunk_size = remaining < VOICE_SERVICE_UPLOAD_CHUNK_SIZE
                                ? remaining
                                : VOICE_SERVICE_UPLOAD_CHUNK_SIZE;
        int written = esp_http_client_write(
            client, (const char *)request_body + sent, (int)chunk_size);
        if (written <= 0) {
            err = ESP_ERR_HTTP_WRITE_DATA;
            break;
        }
        sent += (size_t)written;
        if (sent >= next_log && sent < request_size) {
            log_upload_state("progress", sent, request_size, started_us);
            next_log += VOICE_SERVICE_UPLOAD_LOG_INTERVAL;
        }
    }

    if (restore_power_save) {
        esp_err_t power_err = esp_wifi_set_ps(previous_power_save);
        if (power_err != ESP_OK) {
            ESP_LOGW(TAG, "Could not restore Wi-Fi power save after voice upload: %s",
                     esp_err_to_name(power_err));
        }
    }
    esp_err_t timeout_err = esp_http_client_set_timeout_ms(client, VOICE_SERVICE_TIMEOUT_MS);
    if (err == ESP_OK && timeout_err != ESP_OK) {
        err = timeout_err;
    }

    log_upload_state(err == ESP_OK && sent == request_size ? "completed" : "failed",
                     sent, request_size, started_us);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Voice upload stopped: error=%s sent=%u/%u",
                 esp_err_to_name(err), (unsigned)sent, (unsigned)request_size);
        return err;
    }
    return read_http_response(client);
}

static esp_err_t perform_request(const device_identity_t *identity,
                                 const char *url,
                                 esp_http_client_method_t method,
                                 const uint8_t *request_body,
                                 size_t request_size,
                                 const char *content_type,
                                 const char *accept,
                                 const char *turn_id,
                                 voice_service_buffer_t *output)
{
    if (!device_identity_is_valid(identity) || url == NULL || accept == NULL || output == NULL ||
        (method == HTTP_METHOD_POST && (request_body == NULL || request_size == 0))) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(output, 0, sizeof(*output));
    response_accumulator_t response = {0};
    esp_http_client_config_t config = {
        .url = url,
        .event_handler = response_event_handler,
        .user_data = &response,
        .timeout_ms = VOICE_SERVICE_TIMEOUT_MS,
        .buffer_size = VOICE_SERVICE_HTTP_BUFFER_SIZE,
        .buffer_size_tx = VOICE_SERVICE_HTTP_BUFFER_SIZE,
    };
    device_endpoint_configure_http_client(&config);
    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (client == NULL) {
        return ESP_ERR_NO_MEM;
    }

    char authorization[DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 16] = {0};
    int written = snprintf(authorization, sizeof(authorization), "Bearer %s", identity->access_token);
    esp_err_t err = written > 0 && (size_t)written < sizeof(authorization)
                        ? esp_http_client_set_header(client, "Authorization", authorization)
                        : ESP_ERR_INVALID_SIZE;
    if (err == ESP_OK) {
        err = esp_http_client_set_header(client, "Accept", accept);
    }
    if (err == ESP_OK && content_type != NULL) {
        err = esp_http_client_set_header(client, "Content-Type", content_type);
    }
    if (err == ESP_OK && turn_id != NULL) {
        err = esp_http_client_set_header(client, "X-StackChan-Turn-Id", turn_id);
    }
    if (err == ESP_OK) {
        err = esp_http_client_set_method(client, method);
    }
    bool cancellable = turn_id != NULL;
    if (err == ESP_OK && cancellable) {
        err = set_active_turn_client(client);
    }
    if (err == ESP_OK) {
        err = method == HTTP_METHOD_POST
                  ? perform_streaming_post(client, request_body, request_size)
                  : esp_http_client_perform(client);
    }
    if (cancellable) {
        clear_active_turn_client(client);
    }
    int status = esp_http_client_get_status_code(client);
    (void)esp_http_client_cleanup(client);
    memset(authorization, 0, sizeof(authorization));

    if (err != ESP_OK || response.failed || status != 200 || response.size == 0) {
        heap_caps_free(response.data);
        return err == ESP_OK ? ESP_FAIL : err;
    }
    output->data = response.data;
    output->size = response.size;
    return ESP_OK;
}

esp_err_t voice_service_live_upload_begin(const device_identity_t *identity,
                                          const char *turn_id,
                                          uint32_t sample_rate,
                                          voice_service_stream_frame_handler_t frame_handler,
                                          void *frame_context,
                                          voice_service_live_upload_t **upload_out)
{
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 64] = {0};
    if (!device_identity_is_valid(identity) || turn_id == NULL || strlen(turn_id) != 36 ||
        sample_rate < 8000 || sample_rate > 48000 || frame_handler == NULL ||
        upload_out == NULL || !device_endpoint_build_http_url(
                                  identity->server_base_url,
                                  DEVICE_ENDPOINT_LIVE_VOICE_TURN_PATH,
                                  url,
                                  sizeof(url))) {
        return ESP_ERR_INVALID_ARG;
    }
    *upload_out = NULL;
    voice_service_live_upload_t *upload = heap_caps_calloc(
        1, sizeof(*upload), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (upload == NULL) {
        return ESP_ERR_NO_MEM;
    }
    upload->response.frame_handler = frame_handler;
    upload->response.frame_context = frame_context;
    upload->started_us = esp_timer_get_time();

    esp_http_client_config_t config = {
        .url = url,
        .event_handler = streaming_response_event_handler,
        .user_data = &upload->response,
        .timeout_ms = VOICE_SERVICE_UPLOAD_TIMEOUT_MS,
        .buffer_size = VOICE_SERVICE_HTTP_BUFFER_SIZE,
        .buffer_size_tx = VOICE_SERVICE_HTTP_BUFFER_SIZE,
    };
    device_endpoint_configure_http_client(&config);
    upload->client = esp_http_client_init(&config);
    memset(url, 0, sizeof(url));
    if (upload->client == NULL) {
        destroy_live_upload(upload, false);
        return ESP_ERR_NO_MEM;
    }

    char authorization[DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 16] = {0};
    int written = snprintf(authorization, sizeof(authorization), "Bearer %s", identity->access_token);
    esp_err_t err = written > 0 && (size_t)written < sizeof(authorization)
                        ? esp_http_client_set_header(upload->client, "Authorization", authorization)
                        : ESP_ERR_INVALID_SIZE;
    if (err == ESP_OK) {
        err = esp_http_client_set_header(
            upload->client, "Accept",
            "application/vnd.stackchan.voice-turn-stream, application/vnd.stackchan.voice-turn;q=0.5");
    }
    if (err == ESP_OK) {
        err = esp_http_client_set_header(upload->client, "Content-Type", "audio/wav");
    }
    if (err == ESP_OK) {
        err = esp_http_client_set_header(upload->client, "X-StackChan-Turn-Id", turn_id);
    }
    if (err == ESP_OK) {
        err = esp_http_client_set_method(upload->client, HTTP_METHOD_POST);
    }
    if (err == ESP_OK) {
        err = set_active_turn_client(upload->client);
        upload->active_client = err == ESP_OK;
    }
    memset(authorization, 0, sizeof(authorization));

    upload->previous_power_save = WIFI_PS_NONE;
    upload->restore_power_save = esp_wifi_get_ps(&upload->previous_power_save) == ESP_OK &&
                                 upload->previous_power_save != WIFI_PS_NONE;
    if (err == ESP_OK && upload->restore_power_save) {
        esp_err_t power_err = esp_wifi_set_ps(WIFI_PS_NONE);
        if (power_err != ESP_OK) {
            ESP_LOGW(TAG, "Could not suspend Wi-Fi power save for live voice upload: %s",
                     esp_err_to_name(power_err));
            upload->restore_power_save = false;
        }
    }
    if (err == ESP_OK) {
        err = esp_http_client_open(upload->client, -1);
    }
    uint8_t header[AUDIO_WAV_HEADER_SIZE] = {0};
    if (err == ESP_OK) {
        err = audio_wav_build_pcm16_mono_stream_header(header, sizeof(header), sample_rate);
    }
    if (err == ESP_OK) {
        err = write_transfer_chunk(upload->client, header, sizeof(header));
    }
    memset(header, 0, sizeof(header));
    if (err != ESP_OK) {
        destroy_live_upload(upload, false);
        return err;
    }

    ESP_LOGI(TAG,
             "Voice live upload opened: elapsed_ms=%llu internal_free=%u internal_largest=%u "
             "internal_minimum=%u psram_free=%u",
             (unsigned long long)((esp_timer_get_time() - upload->started_us) / 1000),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_minimum_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    *upload_out = upload;
    return ESP_OK;
}

esp_err_t voice_service_live_upload_write(voice_service_live_upload_t *upload,
                                          const int16_t *samples,
                                          size_t sample_count)
{
    if (upload == NULL || upload->client == NULL || samples == NULL || sample_count == 0 ||
        sample_count > UINT32_MAX / sizeof(int16_t)) {
        return ESP_ERR_INVALID_ARG;
    }
    size_t data_size = sample_count * sizeof(int16_t);
    esp_err_t err = write_transfer_chunk(upload->client, (const uint8_t *)samples, data_size);
    if (err == ESP_OK) {
        upload->pcm_bytes_sent += data_size;
    }
    return err;
}

void voice_service_live_upload_mark_capture_finished_at(voice_service_live_upload_t *upload,
                                                        int64_t capture_finished_us)
{
    if (upload != NULL && upload->capture_finished_us == 0 && capture_finished_us > 0) {
        upload->capture_finished_us = capture_finished_us;
    }
}

esp_err_t voice_service_live_upload_finish(voice_service_live_upload_t *upload,
                                           voice_service_buffer_t *legacy_response,
                                           bool *streamed)
{
    if (upload == NULL || upload->client == NULL || legacy_response == NULL || streamed == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(legacy_response, 0, sizeof(*legacy_response));
    *streamed = false;
    int64_t capture_finished_us = upload->capture_finished_us == 0
                                      ? esp_timer_get_time()
                                      : upload->capture_finished_us;
    static const uint8_t terminal_chunk[] = "0\r\n\r\n";
    esp_err_t err = write_bounded(
        upload->client, terminal_chunk, sizeof(terminal_chunk) - 1);
    int64_t body_finished_us = esp_timer_get_time();
    restore_live_upload_power_save(upload);
    if (err == ESP_OK) {
        err = esp_http_client_set_timeout_ms(upload->client, VOICE_SERVICE_TIMEOUT_MS);
    }
    ESP_LOGI(TAG,
             "Voice live upload %s: pcm_bytes=%u capture_tail_ms=%llu elapsed_ms=%llu "
             "internal_free=%u internal_largest=%u internal_minimum=%u psram_free=%u",
             err == ESP_OK ? "completed" : "failed",
             (unsigned)upload->pcm_bytes_sent,
             (unsigned long long)((body_finished_us - capture_finished_us) / 1000),
             (unsigned long long)((body_finished_us - upload->started_us) / 1000),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_minimum_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    if (err == ESP_OK) {
        err = read_http_response(upload->client);
    }
    int status = esp_http_client_get_status_code(upload->client);
    *streamed = upload->response.streamed;
    bool complete_stream = upload->response.streamed && upload->response.terminal &&
                           upload->response.frame_header_size == 0 &&
                           upload->response.frame_payload == NULL;
    bool complete_legacy = !upload->response.streamed && upload->response.legacy.size > 0;
    if (err != ESP_OK || upload->response.failed || status != 200 ||
        (!complete_stream && !complete_legacy)) {
        destroy_live_upload(upload, false);
        return err == ESP_OK ? ESP_FAIL : err;
    }
    if (complete_legacy) {
        legacy_response->data = upload->response.legacy.data;
        legacy_response->size = upload->response.legacy.size;
        upload->response.legacy.data = NULL;
    }
    destroy_live_upload(upload, false);
    return ESP_OK;
}

void voice_service_live_upload_abort(voice_service_live_upload_t *upload)
{
    if (upload != NULL && upload->client != NULL) {
        (void)esp_http_client_cancel_request(upload->client);
    }
    destroy_live_upload(upload, false);
}

esp_err_t voice_service_send_turn(const device_identity_t *identity,
                                  const char *turn_id,
                                  const uint8_t *wav,
                                  size_t wav_size,
                                  voice_service_buffer_t *response)
{
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 64] = {0};
    if (identity == NULL || turn_id == NULL || strlen(turn_id) != 36 ||
        !device_endpoint_build_http_url(identity->server_base_url,
                                                             DEVICE_ENDPOINT_VOICE_TURN_PATH,
                                                             url,
                                                             sizeof(url))) {
        return ESP_ERR_INVALID_ARG;
    }
    esp_err_t err = perform_request(identity, url, HTTP_METHOD_POST, wav, wav_size, "audio/wav",
                                    "application/vnd.stackchan.voice-turn", turn_id, response);
    memset(url, 0, sizeof(url));
    return err;
}

esp_err_t voice_service_send_turn_streaming(const device_identity_t *identity,
                                            const char *turn_id,
                                            const uint8_t *wav,
                                            size_t wav_size,
                                            voice_service_stream_frame_handler_t frame_handler,
                                            void *frame_context,
                                            voice_service_buffer_t *legacy_response,
                                            bool *streamed)
{
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 64] = {0};
    if (identity == NULL || turn_id == NULL || strlen(turn_id) != 36 || wav == NULL || wav_size == 0 ||
        frame_handler == NULL || legacy_response == NULL || streamed == NULL ||
        !device_endpoint_build_http_url(identity->server_base_url,
                                        DEVICE_ENDPOINT_VOICE_TURN_PATH, url, sizeof(url))) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(legacy_response, 0, sizeof(*legacy_response));
    *streamed = false;
    streaming_response_t response = {
        .frame_handler = frame_handler,
        .frame_context = frame_context,
    };
    esp_http_client_config_t config = {
        .url = url,
        .event_handler = streaming_response_event_handler,
        .user_data = &response,
        .timeout_ms = VOICE_SERVICE_TIMEOUT_MS,
        .buffer_size = VOICE_SERVICE_HTTP_BUFFER_SIZE,
        .buffer_size_tx = VOICE_SERVICE_HTTP_BUFFER_SIZE,
    };
    device_endpoint_configure_http_client(&config);
    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (client == NULL) {
        memset(url, 0, sizeof(url));
        return ESP_ERR_NO_MEM;
    }

    char authorization[DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 16] = {0};
    int written = snprintf(authorization, sizeof(authorization), "Bearer %s", identity->access_token);
    esp_err_t err = written > 0 && (size_t)written < sizeof(authorization)
                        ? esp_http_client_set_header(client, "Authorization", authorization)
                        : ESP_ERR_INVALID_SIZE;
    if (err == ESP_OK) {
        err = esp_http_client_set_header(
            client, "Accept",
            "application/vnd.stackchan.voice-turn-stream, application/vnd.stackchan.voice-turn;q=0.5");
    }
    if (err == ESP_OK) err = esp_http_client_set_header(client, "Content-Type", "audio/wav");
    if (err == ESP_OK) err = esp_http_client_set_header(client, "X-StackChan-Turn-Id", turn_id);
    if (err == ESP_OK) err = esp_http_client_set_method(client, HTTP_METHOD_POST);
    if (err == ESP_OK) err = set_active_turn_client(client);
    if (err == ESP_OK) err = perform_streaming_post(client, wav, wav_size);
    clear_active_turn_client(client);
    int status = esp_http_client_get_status_code(client);
    (void)esp_http_client_cleanup(client);
    memset(authorization, 0, sizeof(authorization));
    memset(url, 0, sizeof(url));
    *streamed = response.streamed;

    bool complete_stream = response.streamed && response.terminal &&
                           response.frame_header_size == 0 && response.frame_payload == NULL;
    bool complete_legacy = !response.streamed && response.legacy.size > 0;
    if (response.frame_payload != NULL) heap_caps_free(response.frame_payload);
    if (err != ESP_OK || response.failed || status != 200 || (!complete_stream && !complete_legacy)) {
        heap_caps_free(response.legacy.data);
        return err == ESP_OK ? ESP_FAIL : err;
    }
    if (complete_legacy) {
        legacy_response->data = response.legacy.data;
        legacy_response->size = response.legacy.size;
    } else {
        heap_caps_free(response.legacy.data);
    }
    return ESP_OK;
}

esp_err_t voice_service_fetch_reminder(const device_identity_t *identity,
                                       const char *reminder_id,
                                       voice_service_buffer_t *response)
{
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 96] = {0};
    if (identity == NULL || !device_endpoint_build_reminder_audio_url(identity->server_base_url,
                                                                       reminder_id,
                                                                       url,
                                                                       sizeof(url))) {
        return ESP_ERR_INVALID_ARG;
    }
    esp_err_t err = perform_request(identity, url, HTTP_METHOD_GET, NULL, 0, NULL, "audio/wav", NULL, response);
    memset(url, 0, sizeof(url));
    return err;
}

void voice_service_release(voice_service_buffer_t *buffer)
{
    if (buffer == NULL) {
        return;
    }
    heap_caps_free(buffer->data);
    memset(buffer, 0, sizeof(*buffer));
}
