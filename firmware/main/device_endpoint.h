#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "esp_http_client.h"
#include "esp_websocket_client.h"

#include "device_identity.h"

#define DEVICE_ENDPOINT_PAIRING_CLAIM_PATH "/api/v1/pairing/claim"
#define DEVICE_ENDPOINT_TOKEN_REFRESH_PATH "/api/v1/devices/token:refresh"
#define DEVICE_ENDPOINT_WEBSOCKET_PATH "/api/v1/ws/device"
#define DEVICE_ENDPOINT_VOICE_TURN_PATH "/api/v1/device/voice/turn"

bool device_endpoint_build_http_url(const char *server_base_url,
                                    const char *path,
                                    char *url,
                                    size_t size);
bool device_endpoint_build_websocket_uri(const device_identity_t *identity,
                                         char *uri,
                                         size_t size);
bool device_endpoint_build_reminder_audio_url(const char *server_base_url,
                                              const char *reminder_id,
                                              char *url,
                                              size_t size);
bool device_endpoint_build_wake_model_url(const char *server_base_url,
                                          const char *job_id,
                                          char *url,
                                          size_t size);
bool device_endpoint_build_expression_pack_url(const char *server_base_url,
                                               const char *pack_id,
                                               char *url,
                                               size_t size);
void device_endpoint_configure_http_client(esp_http_client_config_t *config);
void device_endpoint_configure_websocket_client(esp_websocket_client_config_t *config);
