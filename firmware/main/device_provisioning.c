#include "device_provisioning.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

#include "cJSON.h"
#include "driver/usb_serial_jtag.h"
#include "esp_app_desc.h"
#include "esp_err.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_mac.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "device_identity.h"
#include "device_endpoint.h"
#include "device_transport.h"
#include "strict_json.h"

#define PROVISIONING_TASK_STACK_SIZE 16384
#define PROVISIONING_TASK_PRIORITY 4
#define PROVISIONING_LINE_MAX_LEN 512
#define PROVISIONING_READ_BUFFER_LEN 64
#define CLAIM_RESPONSE_MAX_LEN 2048
#define CLAIM_TIMEOUT_MS 10000
#define WIFI_CONNECT_TIMEOUT_MS 30000
#define WIFI_CONNECT_POLL_MS 200

static const char *TAG = "device_provisioning";

typedef struct {
    char payload[CLAIM_RESPONSE_MAX_LEN];
    size_t length;
    bool overflowed;
} claim_response_t;

static bool has_bounded_length(const char *value, size_t capacity, bool allow_empty)
{
    if (value == NULL) {
        return false;
    }
    size_t length = strnlen(value, capacity);
    return length < capacity && (allow_empty || length > 0);
}

static bool copy_string(char *destination, size_t destination_size, const char *source, bool allow_empty)
{
    if (destination == NULL || !has_bounded_length(source, destination_size, allow_empty)) {
        return false;
    }
    size_t length = strlen(source);
    memcpy(destination, source, length + 1);
    return true;
}

static bool is_valid_pairing_code(const char *pairing_code)
{
    if (!has_bounded_length(pairing_code, DEVICE_PROVISIONING_PAIRING_CODE_MAX_LEN, false)) {
        return false;
    }
    for (const char *cursor = pairing_code; *cursor != '\0'; ++cursor) {
        if (!(isalnum((unsigned char)*cursor) || *cursor == '-' || *cursor == '_')) {
            return false;
        }
    }
    return true;
}

static const cJSON *required_string(const cJSON *root, const char *key)
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(root, key);
    return cJSON_IsString(item) && item->valuestring != NULL ? item : NULL;
}

bool device_provisioning_parse_request(const char *payload,
                                       size_t payload_length,
                                       device_provisioning_request_t *request)
{
    if (payload == NULL || request == NULL || payload_length == 0 || payload_length >= PROVISIONING_LINE_MAX_LEN) {
        return false;
    }
    memset(request, 0, sizeof(*request));
    if (strict_json_contains_decoded_nul_escape(payload, payload_length)) {
        return false;
    }
    const char *parse_end = NULL;
    cJSON *root = cJSON_ParseWithLengthOpts(payload, payload_length, &parse_end, 0);
    if (!cJSON_IsObject(root) ||
        !strict_json_has_only_trailing_whitespace(payload, payload_length, parse_end) ||
        cJSON_GetArraySize(root) != 5) {
        cJSON_Delete(root);
        return false;
    }
    const cJSON *type = required_string(root, "type");
    const cJSON *ssid = required_string(root, "wifiSsid");
    const cJSON *password = required_string(root, "wifiPassword");
    const cJSON *server_base_url = required_string(root, "serverBaseUrl");
    const cJSON *pairing_code = required_string(root, "pairingCode");
    bool valid = type != NULL && strcmp(type->valuestring, "provision") == 0 &&
                 copy_string(request->ssid, sizeof(request->ssid), ssid == NULL ? NULL : ssid->valuestring, false) &&
                 copy_string(request->password, sizeof(request->password),
                             password == NULL ? NULL : password->valuestring, true) &&
                 copy_string(request->server_base_url, sizeof(request->server_base_url),
                             server_base_url == NULL ? NULL : server_base_url->valuestring, false) &&
                 copy_string(request->pairing_code, sizeof(request->pairing_code),
                             pairing_code == NULL ? NULL : pairing_code->valuestring, false) &&
                 device_identity_is_valid_server_base_url(request->server_base_url) &&
                 is_valid_pairing_code(request->pairing_code);
    cJSON_Delete(root);
    if (!valid) {
        memset(request, 0, sizeof(*request));
    }
    return valid;
}

static esp_err_t claim_response_handler(esp_http_client_event_t *event)
{
    if (event->event_id != HTTP_EVENT_ON_DATA || event->user_data == NULL || event->data == NULL || event->data_len <= 0) {
        return ESP_OK;
    }
    claim_response_t *response = event->user_data;
    if (response->length + (size_t)event->data_len >= sizeof(response->payload)) {
        response->overflowed = true;
        return ESP_FAIL;
    }
    memcpy(response->payload + response->length, event->data, (size_t)event->data_len);
    response->length += (size_t)event->data_len;
    response->payload[response->length] = '\0';
    return ESP_OK;
}

static bool build_hardware_id(char *hardware_id, size_t hardware_id_size)
{
    uint8_t mac[6] = {0};
    if (esp_read_mac(mac, ESP_MAC_WIFI_STA) != ESP_OK) {
        return false;
    }
    int written = snprintf(hardware_id, hardware_id_size, "stackchan-%02x-%02x-%02x-%02x-%02x-%02x", mac[0], mac[1],
                           mac[2], mac[3], mac[4], mac[5]);
    return written > 0 && (size_t)written < hardware_id_size;
}

bool device_provisioning_parse_claim_response(const char *json,
                                              size_t length,
                                              const char *server_base_url,
                                              device_identity_t *identity)
{
    if (identity == NULL) {
        return false;
    }
    memset(identity, 0, sizeof(*identity));
    if (json == NULL || length == 0 || server_base_url == NULL ||
        strict_json_contains_decoded_nul_escape(json, length) ||
        !device_identity_is_valid_server_base_url(server_base_url)) {
        return false;
    }
    const char *parse_end = NULL;
    cJSON *root = cJSON_ParseWithLengthOpts(json, length, &parse_end, 0);
    if (!cJSON_IsObject(root) || !strict_json_has_only_trailing_whitespace(json, length, parse_end) ||
        cJSON_GetArraySize(root) != 6) {
        cJSON_Delete(root);
        return false;
    }
    const cJSON *device_id = required_string(root, "deviceId");
    const cJSON *access_token = required_string(root, "accessToken");
    const cJSON *access_token_expires_at = required_string(root, "accessTokenExpiresAt");
    const cJSON *refresh_token = required_string(root, "refreshToken");
    const cJSON *refresh_url = required_string(root, "refreshUrl");
    const cJSON *ws_url = required_string(root, "wsUrl");
    device_identity_t candidate = {0};
    bool valid = device_id != NULL && access_token != NULL && access_token_expires_at != NULL &&
                 refresh_token != NULL && refresh_url != NULL && ws_url != NULL &&
                 strcmp(refresh_url->valuestring, DEVICE_ENDPOINT_TOKEN_REFRESH_PATH) == 0 &&
                 strcmp(ws_url->valuestring, DEVICE_ENDPOINT_WEBSOCKET_PATH) == 0 &&
                 copy_string(candidate.server_base_url, sizeof(candidate.server_base_url), server_base_url, false) &&
                 copy_string(candidate.device_id, sizeof(candidate.device_id), device_id->valuestring, false) &&
                 copy_string(candidate.access_token, sizeof(candidate.access_token), access_token->valuestring, false) &&
                 copy_string(candidate.access_token_expires_at, sizeof(candidate.access_token_expires_at),
                             access_token_expires_at->valuestring, false) &&
                 copy_string(candidate.refresh_token, sizeof(candidate.refresh_token), refresh_token->valuestring, false) &&
                 device_identity_is_valid(&candidate);
    cJSON_Delete(root);
    if (!valid) {
        memset(&candidate, 0, sizeof(candidate));
        return false;
    }
    *identity = candidate;
    memset(&candidate, 0, sizeof(candidate));
    return true;
}

static esp_err_t claim_device(const device_provisioning_request_t *request, device_identity_t *identity)
{
    char claim_url[DEVICE_PROVISIONING_SERVER_BASE_URL_MAX_LEN + 32] = {0};
    char hardware_id[64] = {0};
    char request_body[256] = {0};
    if (!device_endpoint_build_http_url(request->server_base_url, DEVICE_ENDPOINT_PAIRING_CLAIM_PATH,
                                        claim_url, sizeof(claim_url)) ||
        !build_hardware_id(hardware_id, sizeof(hardware_id))) {
        return ESP_ERR_INVALID_ARG;
    }
    const esp_app_desc_t *app_description = esp_app_get_description();
    cJSON *root = cJSON_CreateObject();
    bool encoded = root != NULL && cJSON_AddStringToObject(root, "pairingCode", request->pairing_code) != NULL &&
                   cJSON_AddStringToObject(root, "hardwareId", hardware_id) != NULL &&
                   cJSON_AddStringToObject(root, "firmwareVersion", app_description->version) != NULL &&
                   cJSON_PrintPreallocated(root, request_body, sizeof(request_body), 0) != 0;
    cJSON_Delete(root);
    if (!encoded) {
        return ESP_ERR_NO_MEM;
    }

    claim_response_t response = {0};
    esp_http_client_config_t config = {
        .url = claim_url,
        .method = HTTP_METHOD_POST,
        .timeout_ms = CLAIM_TIMEOUT_MS,
        .event_handler = claim_response_handler,
        .user_data = &response,
        .buffer_size = 512,
    };
    device_endpoint_configure_http_client(&config);
    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (client == NULL) {
        return ESP_ERR_NO_MEM;
    }
    esp_err_t err = esp_http_client_set_header(client, "Content-Type", "application/json");
    if (err == ESP_OK) {
        err = esp_http_client_set_post_field(client, request_body, strlen(request_body));
    }
    if (err == ESP_OK) {
        err = esp_http_client_perform(client);
    }
    int status_code = err == ESP_OK ? esp_http_client_get_status_code(client) : 0;
    esp_http_client_cleanup(client);
    memset(request_body, 0, sizeof(request_body));
    if (err != ESP_OK || status_code != 201) {
        memset(&response, 0, sizeof(response));
        return ESP_FAIL;
    }
    esp_err_t response_err = !response.overflowed && device_provisioning_parse_claim_response(
                                 response.payload, response.length, request->server_base_url, identity)
                                 ? ESP_OK
                                 : ESP_ERR_INVALID_RESPONSE;
    memset(&response, 0, sizeof(response));
    return response_err;
}

static bool wait_for_wifi_connection(void)
{
    uint32_t waited_ms = 0;
    while (waited_ms < WIFI_CONNECT_TIMEOUT_MS) {
        if (device_transport_is_wifi_connected()) {
            return true;
        }
        vTaskDelay(pdMS_TO_TICKS(WIFI_CONNECT_POLL_MS));
        waited_ms += WIFI_CONNECT_POLL_MS;
    }
    return false;
}

static void report_result(const char *status)
{
    char result[96] = {0};
    int written = snprintf(result, sizeof(result), "{\"type\":\"provisioning\",\"status\":\"%s\"}\n", status);
    if (written > 0 && (size_t)written < sizeof(result)) {
        (void)usb_serial_jtag_write_bytes(result, (size_t)written, pdMS_TO_TICKS(1000));
    }
    memset(result, 0, sizeof(result));
}

static void provision(const device_provisioning_request_t *request)
{
    /* A physical re-provisioning request must never race an old device token. */
    if (device_identity_clear() != ESP_OK) {
        report_result("identity_clear_failed");
        return;
    }
    if (device_transport_configure_wifi(request->ssid, request->password) != ESP_OK) {
        report_result("wifi_configuration_failed");
        return;
    }
    if (!wait_for_wifi_connection()) {
        report_result("wifi_connection_failed");
        return;
    }
    device_identity_t identity = {0};
    if (claim_device(request, &identity) != ESP_OK) {
        report_result("claim_failed");
        return;
    }
    if (device_identity_save(&identity) != ESP_OK) {
        report_result("identity_save_failed");
        return;
    }
    memset(&identity, 0, sizeof(identity));
    report_result("complete");
}

static void provisioning_task(void *argument)
{
    (void)argument;
    char line[PROVISIONING_LINE_MAX_LEN] = {0};
    char received[PROVISIONING_READ_BUFFER_LEN] = {0};
    size_t line_length = 0;
    bool line_overflowed = false;
    ESP_LOGI(TAG, "USB provisioning ready");
    for (;;) {
        int received_length = usb_serial_jtag_read_bytes(received, sizeof(received), pdMS_TO_TICKS(100));
        if (received_length <= 0) {
            continue;
        }
        for (int index = 0; index < received_length; ++index) {
            char character = received[index];
            if (character == '\r' || character == '\n') {
                if (line_length == 0 && !line_overflowed) {
                    continue;
                }
                if (line_overflowed) {
                    report_result("invalid_request");
                } else {
                    line[line_length] = '\0';
                    device_provisioning_request_t request = {0};
                    if (!device_provisioning_parse_request(line, line_length, &request)) {
                        report_result("invalid_request");
                    } else {
                        report_result("started");
                        provision(&request);
                    }
                    memset(&request, 0, sizeof(request));
                }
                memset(line, 0, sizeof(line));
                line_length = 0;
                line_overflowed = false;
                continue;
            }
            if (line_length >= sizeof(line) - 1) {
                line_overflowed = true;
                continue;
            }
            if (!line_overflowed) {
                line[line_length++] = character;
            }
        }
        memset(received, 0, sizeof(received));
    }
}

esp_err_t device_provisioning_start(void)
{
    usb_serial_jtag_driver_config_t usb_config = {
        .rx_buffer_size = PROVISIONING_LINE_MAX_LEN,
        .tx_buffer_size = 256,
    };
    esp_err_t driver_err = usb_serial_jtag_driver_install(&usb_config);
    if (driver_err != ESP_OK && driver_err != ESP_ERR_INVALID_STATE) {
        return driver_err;
    }
    return xTaskCreate(provisioning_task, "device_provisioning", PROVISIONING_TASK_STACK_SIZE, NULL,
                       PROVISIONING_TASK_PRIORITY, NULL) == pdPASS
               ? ESP_OK
               : ESP_ERR_NO_MEM;
}
