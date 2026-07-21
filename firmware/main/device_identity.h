#pragma once

#include <stdbool.h>

#include "esp_err.h"

#define DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN 192
#define DEVICE_IDENTITY_DEVICE_ID_MAX_LEN 37
#define DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN 1536
#define DEVICE_IDENTITY_EXPIRY_MAX_LEN 32
#define DEVICE_IDENTITY_REFRESH_TOKEN_MAX_LEN 128

typedef struct {
    char server_base_url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN];
    char device_id[DEVICE_IDENTITY_DEVICE_ID_MAX_LEN];
    char access_token[DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN];
    char access_token_expires_at[DEVICE_IDENTITY_EXPIRY_MAX_LEN];
    char refresh_token[DEVICE_IDENTITY_REFRESH_TOKEN_MAX_LEN];
} device_identity_t;

/**
 * Initializes the default encrypted NVS partition. Only the documented full
 * and version-mismatch errors are recovered by erasing that partition.
 */
esp_err_t device_identity_init_encrypted_nvs(void);

/** Validates a complete identity without reading or writing NVS. */
bool device_identity_is_valid(const device_identity_t *identity);

/** Validates the canonical server origin allowed by the compiled transport mode. */
bool device_identity_is_valid_server_base_url(const char *server_base_url);

/**
 * Loads a complete, validated device identity from the encrypted NVS namespace.
 */
esp_err_t device_identity_load(device_identity_t *identity);

/**
 * Validates and atomically stores all renewable identity fields.
 */
esp_err_t device_identity_save(const device_identity_t *identity);

/**
 * Removes the identity namespace contents without touching other NVS namespaces.
 */
esp_err_t device_identity_clear(void);
