#include "wake_model_ota.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_partition.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "mbedtls/sha256.h"
#include "nvs.h"

#include "device_endpoint.h"
#include "wake_word_model.h"

#define WAKE_MODEL_STATE_MAGIC 0x574d4f54U
#define WAKE_MODEL_STATE_VERSION 1U
#define WAKE_MODEL_NAMESPACE "wake_model"
#define WAKE_MODEL_STATE_KEY "state"
#define WAKE_MODEL_FACTORY_SLOT 0U
#define WAKE_MODEL_OTA_A_SLOT 1U
#define WAKE_MODEL_OTA_B_SLOT 2U
#define WAKE_MODEL_MAX_ARTIFACT_SIZE (1024U * 1024U)
#define WAKE_MODEL_HEADER_READ_SIZE 4096U
#define WAKE_MODEL_DOWNLOAD_BUFFER_SIZE 4096U
#define WAKE_MODEL_MAX_MODELS 5U
#define WAKE_MODEL_MAX_FILES_PER_MODEL 8U
#define WAKE_MODEL_HEALTH_TIMEOUT_MS 20000U
#define WAKE_MODEL_HEALTH_TASK_STACK_SIZE 3072U

typedef struct {
    uint32_t magic;
    uint8_t version;
    uint8_t active_slot;
    uint8_t previous_slot;
    uint8_t pending;
    uint8_t boot_attempt;
    uint8_t report_status;
    uint8_t reserved[2];
    char ota_model_name[2][WAKE_MODEL_OTA_MODEL_NAME_SIZE];
    char ota_sha256[2][WAKE_MODEL_OTA_SHA256_SIZE];
    char pending_job_id[WAKE_MODEL_OTA_JOB_ID_SIZE];
    char report_job_id[WAKE_MODEL_OTA_JOB_ID_SIZE];
    char report_model_name[WAKE_MODEL_OTA_MODEL_NAME_SIZE];
    char report_sha256[WAKE_MODEL_OTA_SHA256_SIZE];
} wake_model_state_t;

static const char *TAG = "wake_model_ota";
static const char *const SLOT_LABELS[] = {"model", "model_a", "model_b"};
static wake_model_state_t s_state;
static SemaphoreHandle_t s_state_mutex;
static bool s_initialized;

static bool is_hex_string(const char *value, size_t expected_length)
{
    if (value == NULL || strlen(value) != expected_length) {
        return false;
    }
    for (size_t index = 0; index < expected_length; ++index) {
        if (!isxdigit((unsigned char)value[index]) || isupper((unsigned char)value[index])) {
            return false;
        }
    }
    return true;
}

static bool is_model_name(const char *value)
{
    size_t length = value == NULL ? 0 : strnlen(value, WAKE_MODEL_OTA_MODEL_NAME_SIZE);
    if (length == 0 || length >= WAKE_MODEL_OTA_MODEL_NAME_SIZE) {
        return false;
    }
    for (size_t index = 0; index < length; ++index) {
        if (!(islower((unsigned char)value[index]) || isdigit((unsigned char)value[index]) ||
              value[index] == '_')) {
            return false;
        }
    }
    return true;
}

static bool is_uuid(const char *value)
{
    if (value == NULL || strlen(value) != 36) {
        return false;
    }
    for (size_t index = 0; index < 36; ++index) {
        if (index == 8 || index == 13 || index == 18 || index == 23) {
            if (value[index] != '-') {
                return false;
            }
        } else if (!isxdigit((unsigned char)value[index])) {
            return false;
        }
    }
    return true;
}

static void reset_state(void)
{
    memset(&s_state, 0, sizeof(s_state));
    s_state.magic = WAKE_MODEL_STATE_MAGIC;
    s_state.version = WAKE_MODEL_STATE_VERSION;
    s_state.active_slot = WAKE_MODEL_FACTORY_SLOT;
    s_state.previous_slot = WAKE_MODEL_FACTORY_SLOT;
}

static bool state_is_valid(const wake_model_state_t *state)
{
    if (state == NULL || state->magic != WAKE_MODEL_STATE_MAGIC ||
        state->version != WAKE_MODEL_STATE_VERSION || state->active_slot > WAKE_MODEL_OTA_B_SLOT ||
        state->previous_slot > WAKE_MODEL_OTA_B_SLOT || state->pending > 1 ||
        state->boot_attempt > 1 || state->report_status > WAKE_MODEL_OTA_REPORT_ROLLED_BACK) {
        return false;
    }
    if (state->active_slot != WAKE_MODEL_FACTORY_SLOT &&
        (!is_model_name(state->ota_model_name[state->active_slot - 1]) ||
         !is_hex_string(state->ota_sha256[state->active_slot - 1], 64))) {
        return false;
    }
    if (state->pending && !is_uuid(state->pending_job_id)) {
        return false;
    }
    if (state->report_status != WAKE_MODEL_OTA_REPORT_NONE &&
        (!is_uuid(state->report_job_id) || !is_model_name(state->report_model_name) ||
         !is_hex_string(state->report_sha256, 64))) {
        return false;
    }
    return true;
}

static esp_err_t save_state_locked(void)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open(WAKE_MODEL_NAMESPACE, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }
    err = nvs_set_blob(handle, WAKE_MODEL_STATE_KEY, &s_state, sizeof(s_state));
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

static void set_report_locked(wake_model_ota_report_status_t status, uint8_t failed_slot)
{
    s_state.report_status = (uint8_t)status;
    memcpy(s_state.report_job_id, s_state.pending_job_id, sizeof(s_state.report_job_id));
    memcpy(s_state.report_model_name, s_state.ota_model_name[failed_slot - 1],
           sizeof(s_state.report_model_name));
    memcpy(s_state.report_sha256, s_state.ota_sha256[failed_slot - 1],
           sizeof(s_state.report_sha256));
}

static esp_err_t rollback_locked(void)
{
    wake_model_state_t previous_state = s_state;
    uint8_t failed_slot = s_state.active_slot;
    if (!s_state.pending || failed_slot == WAKE_MODEL_FACTORY_SLOT) {
        return ESP_ERR_INVALID_STATE;
    }
    set_report_locked(WAKE_MODEL_OTA_REPORT_ROLLED_BACK, failed_slot);
    s_state.active_slot = s_state.previous_slot;
    s_state.previous_slot = WAKE_MODEL_FACTORY_SLOT;
    s_state.pending = 0;
    s_state.boot_attempt = 0;
    memset(s_state.pending_job_id, 0, sizeof(s_state.pending_job_id));
    esp_err_t err = save_state_locked();
    if (err != ESP_OK) {
        s_state = previous_state;
    }
    return err;
}

esp_err_t wake_model_ota_init(void)
{
    if (s_state_mutex != NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    s_state_mutex = xSemaphoreCreateMutex();
    if (s_state_mutex == NULL) {
        return ESP_ERR_NO_MEM;
    }

    nvs_handle_t handle;
    esp_err_t err = nvs_open(WAKE_MODEL_NAMESPACE, NVS_READONLY, &handle);
    if (err == ESP_OK) {
        size_t size = sizeof(s_state);
        err = nvs_get_blob(handle, WAKE_MODEL_STATE_KEY, &s_state, &size);
        nvs_close(handle);
        if (err == ESP_OK && (size != sizeof(s_state) || !state_is_valid(&s_state))) {
            err = ESP_ERR_INVALID_STATE;
        }
    }
    if (err == ESP_ERR_NVS_NOT_FOUND || err == ESP_ERR_NVS_INVALID_HANDLE ||
        err == ESP_ERR_INVALID_STATE) {
        reset_state();
        if (xSemaphoreTake(s_state_mutex, portMAX_DELAY) == pdTRUE) {
            err = save_state_locked();
            xSemaphoreGive(s_state_mutex);
        }
    }
    if (err != ESP_OK) {
        reset_state();
        return err;
    }

    if (s_state.pending) {
        if (s_state.boot_attempt >= 1) {
            if (xSemaphoreTake(s_state_mutex, portMAX_DELAY) == pdTRUE) {
                err = rollback_locked();
                xSemaphoreGive(s_state_mutex);
            }
            ESP_LOGW(TAG, "Pending wake model was not confirmed; previous slot restored");
        } else if (xSemaphoreTake(s_state_mutex, portMAX_DELAY) == pdTRUE) {
            wake_model_state_t previous_state = s_state;
            s_state.boot_attempt = 1;
            err = save_state_locked();
            if (err != ESP_OK) {
                s_state = previous_state;
            }
            xSemaphoreGive(s_state_mutex);
        }
    }
    s_initialized = err == ESP_OK;
    return err;
}

const char *wake_model_ota_active_partition_label(void)
{
    if (!s_initialized) {
        return SLOT_LABELS[WAKE_MODEL_FACTORY_SLOT];
    }
    return SLOT_LABELS[s_state.active_slot <= WAKE_MODEL_OTA_B_SLOT ? s_state.active_slot : 0];
}

const char *wake_model_ota_active_model_name(void)
{
    if (!s_initialized) {
        return CONFIG_STACKCHAN_WAKE_WORD_MODEL;
    }
    if (s_state.active_slot == WAKE_MODEL_OTA_A_SLOT || s_state.active_slot == WAKE_MODEL_OTA_B_SLOT) {
        const char *name = s_state.ota_model_name[s_state.active_slot - 1];
        if (is_model_name(name)) {
            return name;
        }
    }
    return CONFIG_STACKCHAN_WAKE_WORD_MODEL;
}

bool wake_model_ota_is_pending(void)
{
    bool pending = false;
    if (s_initialized && s_state_mutex != NULL &&
        xSemaphoreTake(s_state_mutex, portMAX_DELAY) == pdTRUE) {
        pending = s_state.pending != 0;
        xSemaphoreGive(s_state_mutex);
    }
    return pending;
}

esp_err_t wake_model_ota_confirm_active(void)
{
    if (!s_initialized || s_state_mutex == NULL ||
        xSemaphoreTake(s_state_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    esp_err_t err = ESP_ERR_INVALID_STATE;
    if (s_state.pending && s_state.active_slot != WAKE_MODEL_FACTORY_SLOT) {
        wake_model_state_t previous_state = s_state;
        set_report_locked(WAKE_MODEL_OTA_REPORT_INSTALLED, s_state.active_slot);
        s_state.pending = 0;
        s_state.boot_attempt = 0;
        memset(s_state.pending_job_id, 0, sizeof(s_state.pending_job_id));
        err = save_state_locked();
        if (err != ESP_OK) {
            s_state = previous_state;
        }
    }
    xSemaphoreGive(s_state_mutex);
    return err;
}

void wake_model_ota_rollback_and_restart(void)
{
    if (s_initialized && s_state_mutex != NULL &&
        xSemaphoreTake(s_state_mutex, portMAX_DELAY) == pdTRUE) {
        esp_err_t err = rollback_locked();
        xSemaphoreGive(s_state_mutex);
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "Wake model rollback state could not be saved: %s", esp_err_to_name(err));
        }
    }
    vTaskDelay(pdMS_TO_TICKS(100));
    esp_restart();
    abort();
}

static void health_guard_task(void *argument)
{
    (void)argument;
    vTaskDelay(pdMS_TO_TICKS(WAKE_MODEL_HEALTH_TIMEOUT_MS));
    if (wake_model_ota_is_pending()) {
        ESP_LOGE(TAG, "Wake model health confirmation timed out; rolling back");
        wake_model_ota_rollback_and_restart();
    }
    vTaskDelete(NULL);
}

esp_err_t wake_model_ota_start_health_guard(void)
{
    if (!wake_model_ota_is_pending()) {
        return ESP_OK;
    }
    return xTaskCreate(health_guard_task, "wake_model_guard", WAKE_MODEL_HEALTH_TASK_STACK_SIZE,
                       NULL, 4, NULL) == pdPASS ? ESP_OK : ESP_ERR_NO_MEM;
}

static uint32_t read_u32_le(const uint8_t *data)
{
    return (uint32_t)data[0] | ((uint32_t)data[1] << 8) | ((uint32_t)data[2] << 16) |
           ((uint32_t)data[3] << 24);
}

static bool read_fixed_name(const uint8_t *header, size_t header_size, size_t *offset,
                            char *name, size_t name_size)
{
    if (*offset > header_size || header_size - *offset < 32 || name_size < 33) {
        return false;
    }
    size_t length = 0;
    while (length < 32 && header[*offset + length] != 0) {
        unsigned char character = header[*offset + length];
        if (character < 0x21 || character > 0x7e) {
            return false;
        }
        length++;
    }
    if (length == 0 || length >= 32) {
        return false;
    }
    for (size_t index = length; index < 32; ++index) {
        if (header[*offset + index] != 0) {
            return false;
        }
    }
    memcpy(name, header + *offset, length);
    name[length] = '\0';
    *offset += 32;
    return true;
}

static bool validate_partition_package(const esp_partition_t *partition,
                                       size_t artifact_size,
                                       const char *expected_model_name)
{
    uint8_t *header = malloc(WAKE_MODEL_HEADER_READ_SIZE);
    if (header == NULL) {
        return false;
    }
    size_t read_size = artifact_size < WAKE_MODEL_HEADER_READ_SIZE ? artifact_size : WAKE_MODEL_HEADER_READ_SIZE;
    bool valid = esp_partition_read(partition, 0, header, read_size) == ESP_OK && read_size >= 4;
    size_t offset = 0;
    uint32_t model_count = valid ? read_u32_le(header) : 0;
    offset = 4;
    valid = valid && model_count > 0 && model_count <= WAKE_MODEL_MAX_MODELS;
    bool found_expected = false;
    bool found_fallback = false;
    for (uint32_t model_index = 0; valid && model_index < model_count; ++model_index) {
        char model_name[33] = {0};
        valid = read_fixed_name(header, read_size, &offset, model_name, sizeof(model_name));
        if (!valid || offset > read_size || read_size - offset < 4) {
            valid = false;
            break;
        }
        uint32_t file_count = read_u32_le(header + offset);
        offset += 4;
        valid = file_count > 0 && file_count <= WAKE_MODEL_MAX_FILES_PER_MODEL;
        bool metadata = false;
        bool data = false;
        bool index = false;
        for (uint32_t file_index = 0; valid && file_index < file_count; ++file_index) {
            char file_name[33] = {0};
            valid = read_fixed_name(header, read_size, &offset, file_name, sizeof(file_name));
            if (!valid || offset > read_size || read_size - offset < 8) {
                valid = false;
                break;
            }
            uint32_t start = read_u32_le(header + offset);
            uint32_t length = read_u32_le(header + offset + 4);
            offset += 8;
            valid = length > 0 && start <= artifact_size && length <= artifact_size - start;
            metadata = metadata || strcmp(file_name, "_MODEL_INFO_") == 0;
            size_t file_name_length = strlen(file_name);
            data = data || (file_name_length >= 5 && strcmp(file_name + file_name_length - 5, "_data") == 0);
            index = index || (file_name_length >= 6 && strcmp(file_name + file_name_length - 6, "_index") == 0);
        }
        valid = valid && metadata && data && index;
        found_expected = found_expected || strcmp(model_name, expected_model_name) == 0;
        found_fallback = found_fallback || strcmp(model_name, WAKE_WORD_DEFAULT_MODEL_NAME) == 0;
    }
    free(header);
    return valid && found_expected && found_fallback;
}

static void digest_to_hex(const unsigned char digest[32], char output[WAKE_MODEL_OTA_SHA256_SIZE])
{
    static const char digits[] = "0123456789abcdef";
    for (size_t index = 0; index < 32; ++index) {
        output[index * 2] = digits[digest[index] >> 4];
        output[index * 2 + 1] = digits[digest[index] & 0x0f];
    }
    output[64] = '\0';
}

static esp_err_t download_to_partition(const device_identity_t *identity,
                                       const wake_model_ota_request_t *request,
                                       const esp_partition_t *partition)
{
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 96] = {0};
    if (!device_endpoint_build_wake_model_url(identity->server_base_url, request->job_id,
                                               url, sizeof(url))) {
        return ESP_ERR_INVALID_ARG;
    }
    esp_http_client_config_t config = {
        .url = url,
        .timeout_ms = 60000,
        .buffer_size = WAKE_MODEL_DOWNLOAD_BUFFER_SIZE,
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
        err = esp_http_client_set_header(client, "Accept", "application/vnd.stackchan.wake-model");
    }
    if (err == ESP_OK) {
        err = esp_http_client_open(client, 0);
    }
    int64_t content_length = err == ESP_OK ? esp_http_client_fetch_headers(client) : -1;
    int status = err == ESP_OK ? esp_http_client_get_status_code(client) : 0;
    if (err != ESP_OK || status != 200 || content_length != (int64_t)request->artifact_size) {
        err = err == ESP_OK ? ESP_ERR_INVALID_RESPONSE : err;
        goto cleanup;
    }
    err = esp_partition_erase_range(partition, 0, partition->size);
    if (err != ESP_OK) {
        goto cleanup;
    }

    uint8_t *buffer = malloc(WAKE_MODEL_DOWNLOAD_BUFFER_SIZE);
    if (buffer == NULL) {
        err = ESP_ERR_NO_MEM;
        goto cleanup;
    }
    mbedtls_sha256_context sha256;
    mbedtls_sha256_init(&sha256);
    if (mbedtls_sha256_starts(&sha256, 0) != 0) {
        free(buffer);
        mbedtls_sha256_free(&sha256);
        err = ESP_FAIL;
        goto cleanup;
    }
    size_t offset = 0;
    while (offset < request->artifact_size) {
        size_t remaining = request->artifact_size - offset;
        int requested = (int)(remaining < WAKE_MODEL_DOWNLOAD_BUFFER_SIZE
                                  ? remaining
                                  : WAKE_MODEL_DOWNLOAD_BUFFER_SIZE);
        int received = esp_http_client_read(client, (char *)buffer, requested);
        if (received <= 0 || esp_partition_write(partition, offset, buffer, (size_t)received) != ESP_OK ||
            mbedtls_sha256_update(&sha256, buffer, (size_t)received) != 0) {
            err = ESP_FAIL;
            break;
        }
        offset += (size_t)received;
    }
    unsigned char digest[32] = {0};
    if (err == ESP_OK && mbedtls_sha256_finish(&sha256, digest) != 0) {
        err = ESP_FAIL;
    }
    mbedtls_sha256_free(&sha256);
    free(buffer);
    char actual_sha256[WAKE_MODEL_OTA_SHA256_SIZE] = {0};
    digest_to_hex(digest, actual_sha256);
    if (err == ESP_OK && (offset != request->artifact_size ||
                          strcmp(actual_sha256, request->sha256) != 0)) {
        err = ESP_ERR_INVALID_CRC;
    }

cleanup:
    (void)esp_http_client_close(client);
    (void)esp_http_client_cleanup(client);
    memset(authorization, 0, sizeof(authorization));
    memset(url, 0, sizeof(url));
    return err;
}

esp_err_t wake_model_ota_install(const device_identity_t *identity,
                                 const wake_model_ota_request_t *request)
{
    if (!device_identity_is_valid(identity) || request == NULL || !is_uuid(request->job_id) ||
        !is_model_name(request->model_name) || !is_hex_string(request->sha256, 64) ||
        request->artifact_size == 0 || request->artifact_size > WAKE_MODEL_MAX_ARTIFACT_SIZE ||
        !s_initialized || s_state_mutex == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    uint8_t active_slot = WAKE_MODEL_FACTORY_SLOT;
    if (xSemaphoreTake(s_state_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    if (s_state.pending) {
        xSemaphoreGive(s_state_mutex);
        return ESP_ERR_INVALID_STATE;
    }
    active_slot = s_state.active_slot;
    xSemaphoreGive(s_state_mutex);

    uint8_t target_slot = active_slot == WAKE_MODEL_OTA_A_SLOT
                              ? WAKE_MODEL_OTA_B_SLOT
                              : WAKE_MODEL_OTA_A_SLOT;
    const esp_partition_t *partition = esp_partition_find_first(
        ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_ANY, SLOT_LABELS[target_slot]);
    if (partition == NULL || request->artifact_size > partition->size) {
        return ESP_ERR_NOT_FOUND;
    }
    esp_err_t err = download_to_partition(identity, request, partition);
    if (err != ESP_OK || !validate_partition_package(partition, request->artifact_size,
                                                      request->model_name)) {
        return err == ESP_OK ? ESP_ERR_INVALID_RESPONSE : err;
    }

    if (xSemaphoreTake(s_state_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    wake_model_state_t previous_state = s_state;
    s_state.previous_slot = s_state.active_slot;
    s_state.active_slot = target_slot;
    s_state.pending = 1;
    s_state.boot_attempt = 0;
    memcpy(s_state.ota_model_name[target_slot - 1], request->model_name,
           sizeof(s_state.ota_model_name[target_slot - 1]));
    memcpy(s_state.ota_sha256[target_slot - 1], request->sha256,
           sizeof(s_state.ota_sha256[target_slot - 1]));
    memcpy(s_state.pending_job_id, request->job_id, sizeof(s_state.pending_job_id));
    err = save_state_locked();
    if (err != ESP_OK) {
        s_state = previous_state;
    }
    xSemaphoreGive(s_state_mutex);
    return err;
}

bool wake_model_ota_get_report(wake_model_ota_report_t *report)
{
    if (report == NULL || !s_initialized || s_state_mutex == NULL ||
        xSemaphoreTake(s_state_mutex, portMAX_DELAY) != pdTRUE) {
        return false;
    }
    bool available = s_state.report_status != WAKE_MODEL_OTA_REPORT_NONE;
    if (available) {
        memset(report, 0, sizeof(*report));
        report->status = (wake_model_ota_report_status_t)s_state.report_status;
        memcpy(report->job_id, s_state.report_job_id, sizeof(report->job_id));
        memcpy(report->model_name, s_state.report_model_name, sizeof(report->model_name));
        memcpy(report->sha256, s_state.report_sha256, sizeof(report->sha256));
    }
    xSemaphoreGive(s_state_mutex);
    return available;
}
