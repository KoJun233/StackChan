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

#define VOICE_SERVICE_MAX_RESPONSE_SIZE (2U * 1024U * 1024U)
#define VOICE_SERVICE_INITIAL_CAPACITY 8192U
#define VOICE_SERVICE_TIMEOUT_MS 45000

typedef struct {
    uint8_t *data;
    size_t size;
    size_t capacity;
    bool failed;
} response_accumulator_t;

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
