#include "device_credentials.h"

#include <stdio.h>
#include <string.h>

#include "cJSON.h"
#include "esp_http_client.h"

#include "device_endpoint.h"
#include "strict_json.h"

#define REFRESH_RESPONSE_MAX_LEN 2048
#define REFRESH_REQUEST_MAX_LEN 256
#define REFRESH_TIMEOUT_MS 10000

typedef struct {
    char payload[REFRESH_RESPONSE_MAX_LEN];
    size_t length;
    bool overflowed;
} refresh_response_t;

static bool copy_string(char *destination, size_t destination_size, const char *source)
{
    if (destination == NULL || source == NULL) {
        return false;
    }
    size_t length = strnlen(source, destination_size);
    if (length == 0 || length >= destination_size) {
        return false;
    }
    memcpy(destination, source, length + 1);
    return true;
}

static const cJSON *required_string(const cJSON *root, const char *key)
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(root, key);
    return cJSON_IsString(item) && item->valuestring != NULL ? item : NULL;
}

bool device_credentials_parse_refresh_response(const char *json,
                                               size_t length,
                                               device_identity_t *identity)
{
    if (json == NULL || length == 0 || identity == NULL || !device_identity_is_valid(identity) ||
        strict_json_contains_decoded_nul_escape(json, length)) {
        return false;
    }
    const char *parse_end = NULL;
    cJSON *root = cJSON_ParseWithLengthOpts(json, length, &parse_end, 0);
    if (!cJSON_IsObject(root) || !strict_json_has_only_trailing_whitespace(json, length, parse_end) ||
        cJSON_GetArraySize(root) != 3) {
        cJSON_Delete(root);
        return false;
    }
    const cJSON *access_token = required_string(root, "accessToken");
    const cJSON *access_token_expires_at = required_string(root, "accessTokenExpiresAt");
    const cJSON *ws_url = required_string(root, "wsUrl");
    device_identity_t candidate = *identity;
    bool valid = access_token != NULL && access_token_expires_at != NULL && ws_url != NULL &&
                 strcmp(ws_url->valuestring, DEVICE_ENDPOINT_WEBSOCKET_PATH) == 0 &&
                 copy_string(candidate.access_token, sizeof(candidate.access_token), access_token->valuestring) &&
                 copy_string(candidate.access_token_expires_at, sizeof(candidate.access_token_expires_at),
                             access_token_expires_at->valuestring) &&
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

static bool build_refresh_request(const device_identity_t *identity, char *request, size_t size)
{
    int written = snprintf(request, size, "{\"deviceId\":\"%s\",\"refreshToken\":\"%s\"}",
                           identity->device_id, identity->refresh_token);
    return written > 0 && (size_t)written < size;
}

static esp_err_t refresh_response_handler(esp_http_client_event_t *event)
{
    if (event->event_id != HTTP_EVENT_ON_DATA || event->user_data == NULL || event->data == NULL ||
        event->data_len <= 0) {
        return ESP_OK;
    }
    refresh_response_t *response = event->user_data;
    if (response->overflowed) {
        return ESP_OK;
    }
    if (response->length + (size_t)event->data_len >= sizeof(response->payload)) {
        response->overflowed = true;
        return ESP_OK;
    }
    memcpy(response->payload + response->length, event->data, (size_t)event->data_len);
    response->length += (size_t)event->data_len;
    response->payload[response->length] = '\0';
    return ESP_OK;
}

device_credential_refresh_result_t device_credentials_classify_refresh_result(bool transport_succeeded,
                                                                              int status_code,
                                                                              bool response_overflowed,
                                                                              bool response_valid)
{
    if (status_code == 401 || status_code == 403) {
        return DEVICE_CREDENTIAL_REPAIR_REQUIRED;
    }
    if (transport_succeeded && status_code == 200 && !response_overflowed && response_valid) {
        return DEVICE_CREDENTIAL_REFRESHED;
    }
    return DEVICE_CREDENTIAL_TEMPORARY_FAILURE;
}

device_credential_refresh_result_t device_credentials_refresh(device_identity_t *identity)
{
    if (identity == NULL || !device_identity_is_valid(identity)) {
        return DEVICE_CREDENTIAL_REPAIR_REQUIRED;
    }

    char refresh_url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 40] = {0};
    char request_body[REFRESH_REQUEST_MAX_LEN] = {0};
    refresh_response_t response = {0};
    esp_http_client_handle_t client = NULL;
    device_credential_refresh_result_t result = DEVICE_CREDENTIAL_TEMPORARY_FAILURE;
    if (!device_endpoint_build_http_url(identity->server_base_url, DEVICE_ENDPOINT_TOKEN_REFRESH_PATH,
                                        refresh_url, sizeof(refresh_url)) ||
        !build_refresh_request(identity, request_body, sizeof(request_body))) {
        goto cleanup;
    }

    esp_http_client_config_t config = {
        .url = refresh_url,
        .method = HTTP_METHOD_POST,
        .timeout_ms = REFRESH_TIMEOUT_MS,
        .event_handler = refresh_response_handler,
        .user_data = &response,
        .buffer_size = 512,
    };
    device_endpoint_configure_http_client(&config);
    client = esp_http_client_init(&config);
    if (client == NULL) {
        goto cleanup;
    }
    esp_err_t err = esp_http_client_set_header(client, "Content-Type", "application/json");
    if (err == ESP_OK) {
        err = esp_http_client_set_post_field(client, request_body, strlen(request_body));
    }
    if (err == ESP_OK) {
        err = esp_http_client_perform(client);
    }
    int status_code = esp_http_client_get_status_code(client);
    device_identity_t candidate = *identity;
    bool response_valid = err == ESP_OK && status_code == 200 && !response.overflowed && response.length > 0 &&
                          device_credentials_parse_refresh_response(response.payload, response.length, &candidate);
    result = device_credentials_classify_refresh_result(err == ESP_OK, status_code, response.overflowed,
                                                        response_valid);
    if (result == DEVICE_CREDENTIAL_REFRESHED) {
        *identity = candidate;
    }
    memset(&candidate, 0, sizeof(candidate));

cleanup:
    if (client != NULL) {
        esp_http_client_cleanup(client);
    }
    memset(refresh_url, 0, sizeof(refresh_url));
    memset(request_body, 0, sizeof(request_body));
    memset(&response, 0, sizeof(response));
    return result;
}
