#include "voice_service.h"

#include <stdbool.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#include "esp_heap_caps.h"
#include "esp_http_client.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "device_endpoint.h"
#include "voice_protocol.h"

#define VOICE_SERVICE_MAX_RESPONSE_SIZE (2U * 1024U * 1024U)
#define VOICE_SERVICE_INITIAL_CAPACITY 8192U
#define VOICE_SERVICE_TIMEOUT_MS 90000

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
            esp_err_t err = response->frame_handler(
                type, response->frame_payload, response->frame_payload_size, response->frame_context);
            heap_caps_free(response->frame_payload);
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
        .buffer_size = 4096,
        .buffer_size_tx = 4096,
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
    if (err == ESP_OK && method == HTTP_METHOD_POST) {
        if (request_size > INT_MAX) {
            err = ESP_ERR_INVALID_SIZE;
        } else {
            err = esp_http_client_set_post_field(client, (const char *)request_body, (int)request_size);
        }
    }
    bool cancellable = turn_id != NULL;
    if (err == ESP_OK && cancellable) {
        err = set_active_turn_client(client);
    }
    if (err == ESP_OK) {
        err = esp_http_client_perform(client);
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
        .buffer_size = 4096,
        .buffer_size_tx = 4096,
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
    if (err == ESP_OK) {
        err = wav_size > INT_MAX
                  ? ESP_ERR_INVALID_SIZE
                  : esp_http_client_set_post_field(client, (const char *)wav, (int)wav_size);
    }
    if (err == ESP_OK) err = set_active_turn_client(client);
    if (err == ESP_OK) err = esp_http_client_perform(client);
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
