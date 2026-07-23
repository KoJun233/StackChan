#include "device_endpoint.h"

#include <ctype.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "esp_crt_bundle.h"

#if CONFIG_STACKCHAN_LAN_TEST_CERT
extern const uint8_t lan_test_server_pem_start[] asm("_binary_lan_test_server_pem_start");
#endif

static bool is_supported_http_path(const char *path)
{
    return path != NULL &&
           (strcmp(path, DEVICE_ENDPOINT_PAIRING_CLAIM_PATH) == 0 ||
            strcmp(path, DEVICE_ENDPOINT_TOKEN_REFRESH_PATH) == 0 ||
            strcmp(path, DEVICE_ENDPOINT_VOICE_TURN_PATH) == 0);
}

static bool is_canonical_uuid(const char *value)
{
    if (value == NULL || strlen(value) != 36) {
        return false;
    }
    for (size_t index = 0; index < 36; ++index) {
        unsigned char character = (unsigned char)value[index];
        if (index == 8 || index == 13 || index == 18 || index == 23) {
            if (character != '-') {
                return false;
            }
        } else if (!isxdigit(character)) {
            return false;
        }
    }
    return true;
}

static size_t canonical_origin_length(const char *server_base_url)
{
    size_t length = strlen(server_base_url);
    return length > 0 && server_base_url[length - 1] == '/' ? length - 1 : length;
}

bool device_endpoint_build_http_url(const char *server_base_url,
                                    const char *path,
                                    char *url,
                                    size_t size)
{
    if (url == NULL || size == 0) {
        return false;
    }
    url[0] = '\0';
    if (!device_identity_is_valid_server_base_url(server_base_url) || !is_supported_http_path(path)) {
        return false;
    }
    int written = snprintf(url, size, "%.*s%s", (int)canonical_origin_length(server_base_url),
                           server_base_url, path);
    return written > 0 && (size_t)written < size;
}

bool device_endpoint_build_websocket_uri(const device_identity_t *identity,
                                         char *uri,
                                         size_t size)
{
    if (uri == NULL || size == 0) {
        return false;
    }
    uri[0] = '\0';
    if (!device_identity_is_valid(identity)) {
        return false;
    }
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    const char *source_scheme = "http://";
    const char *target_scheme = "ws://";
#else
    const char *source_scheme = "https://";
    const char *target_scheme = "wss://";
#endif
    size_t source_scheme_length = strlen(source_scheme);
    size_t origin_length = canonical_origin_length(identity->server_base_url);
    int written = snprintf(uri, size, "%s%.*s%s", target_scheme,
                           (int)(origin_length - source_scheme_length),
                           identity->server_base_url + source_scheme_length,
                           DEVICE_ENDPOINT_WEBSOCKET_PATH);
    return written > 0 && (size_t)written < size;
}

bool device_endpoint_build_reminder_audio_url(const char *server_base_url,
                                              const char *reminder_id,
                                              char *url,
                                              size_t size)
{
    if (url == NULL || size == 0) {
        return false;
    }
    url[0] = '\0';
    if (!device_identity_is_valid_server_base_url(server_base_url) || !is_canonical_uuid(reminder_id)) {
        return false;
    }
    int written = snprintf(url, size, "%.*s/api/v1/device/reminders/%s/audio",
                           (int)canonical_origin_length(server_base_url), server_base_url, reminder_id);
    return written > 0 && (size_t)written < size;
}

bool device_endpoint_build_wake_model_url(const char *server_base_url,
                                          const char *job_id,
                                          char *url,
                                          size_t size)
{
    if (url == NULL || size == 0) {
        return false;
    }
    url[0] = '\0';
    if (!device_identity_is_valid_server_base_url(server_base_url) || !is_canonical_uuid(job_id)) {
        return false;
    }
    int written = snprintf(url, size, "%.*s/api/v1/device/wake-models/%s/artifact",
                           (int)canonical_origin_length(server_base_url), server_base_url, job_id);
    return written > 0 && (size_t)written < size;
}

void device_endpoint_configure_http_client(esp_http_client_config_t *config)
{
    if (config == NULL) {
        return;
    }
    config->disable_auto_redirect = true;
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    config->transport_type = HTTP_TRANSPORT_OVER_TCP;
#else
    config->transport_type = HTTP_TRANSPORT_OVER_SSL;
#if CONFIG_STACKCHAN_LAN_TEST_CERT
    config->cert_pem = (const char *)lan_test_server_pem_start;
#else
    config->crt_bundle_attach = esp_crt_bundle_attach;
#endif
#endif
}

void device_endpoint_configure_websocket_client(esp_websocket_client_config_t *config)
{
    if (config == NULL) {
        return;
    }
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    config->transport = WEBSOCKET_TRANSPORT_OVER_TCP;
#else
    config->transport = WEBSOCKET_TRANSPORT_OVER_SSL;
#if CONFIG_STACKCHAN_LAN_TEST_CERT
    config->cert_pem = (const char *)lan_test_server_pem_start;
#else
    config->crt_bundle_attach = esp_crt_bundle_attach;
#endif
#endif
}
