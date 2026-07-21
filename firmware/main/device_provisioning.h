#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"

#include "device_identity.h"

#define DEVICE_PROVISIONING_SSID_MAX_LEN 33
#define DEVICE_PROVISIONING_PASSWORD_MAX_LEN 64
#define DEVICE_PROVISIONING_SERVER_BASE_URL_MAX_LEN 192
#define DEVICE_PROVISIONING_PAIRING_CODE_MAX_LEN 13

typedef struct {
    char ssid[DEVICE_PROVISIONING_SSID_MAX_LEN];
    char password[DEVICE_PROVISIONING_PASSWORD_MAX_LEN];
    char server_base_url[DEVICE_PROVISIONING_SERVER_BASE_URL_MAX_LEN];
    char pairing_code[DEVICE_PROVISIONING_PAIRING_CODE_MAX_LEN];
} device_provisioning_request_t;

/**
 * Parses one strict USB provisioning JSON request without retaining its source
 * buffer. The command must have type "provision" and exactly the documented
 * Wi-Fi, server, and pairing fields.
 */
bool device_provisioning_parse_request(const char *payload,
                                       size_t payload_length,
                                       device_provisioning_request_t *request);

/** Parses one strict six-field pairing claim response into a complete identity. */
bool device_provisioning_parse_claim_response(const char *json,
                                              size_t length,
                                              const char *server_base_url,
                                              device_identity_t *identity);

/** Starts the bounded USB provisioning task after Wi-Fi transport startup. */
esp_err_t device_provisioning_start(void);
