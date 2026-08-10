#include "firmware_ota.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_app_desc.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_ota_ops.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "mbedtls/sha256.h"
#include "nvs.h"

#include "device_endpoint.h"

#if !CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE
#error "Application OTA requires CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y"
#endif

#define FIRMWARE_STATE_MAGIC 0x46574f54U
#define FIRMWARE_STATE_VERSION 1U
#define FIRMWARE_NAMESPACE "firmware_ota"
#define FIRMWARE_STATE_KEY "state"
#define FIRMWARE_MAX_ARTIFACT_SIZE (3U * 1024U * 1024U)
#define FIRMWARE_DOWNLOAD_BUFFER_SIZE 4096U
#define FIRMWARE_HEALTH_TIMEOUT_MS 30000U
#define FIRMWARE_HEALTH_TASK_STACK_SIZE 3072U

typedef struct {
    uint32_t magic;
    uint8_t state_version;
    uint8_t pending;
    uint8_t report_status;
    uint8_t reserved;
    char pending_job_id[FIRMWARE_OTA_JOB_ID_SIZE];
    char pending_version[FIRMWARE_OTA_VERSION_SIZE];
    char pending_sha256[FIRMWARE_OTA_SHA256_SIZE];
    char report_job_id[FIRMWARE_OTA_JOB_ID_SIZE];
    char report_version[FIRMWARE_OTA_VERSION_SIZE];
    char report_sha256[FIRMWARE_OTA_SHA256_SIZE];
} firmware_ota_state_t;

static const char *TAG = "firmware_ota";
static firmware_ota_state_t s_state;
static SemaphoreHandle_t s_mutex;
static bool s_initialized;

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

static bool is_version(const char *value)
{
    size_t length = value == NULL ? 0 : strnlen(value, FIRMWARE_OTA_VERSION_SIZE);
    if (length == 0 || length >= FIRMWARE_OTA_VERSION_SIZE || !isalnum((unsigned char)value[0])) {
        return false;
    }
    for (size_t index = 1; index < length; ++index) {
        unsigned char character = (unsigned char)value[index];
        if (!(isalnum(character) || character == '.' || character == '_' || character == '-')) {
            return false;
        }
    }
    return true;
}

static bool is_sha256(const char *value)
{
    if (value == NULL || strlen(value) != 64) {
        return false;
    }
    for (size_t index = 0; index < 64; ++index) {
        if (!isxdigit((unsigned char)value[index]) || isupper((unsigned char)value[index])) {
            return false;
        }
    }
    return true;
}

static void reset_state(void)
{
    memset(&s_state, 0, sizeof(s_state));
    s_state.magic = FIRMWARE_STATE_MAGIC;
    s_state.state_version = FIRMWARE_STATE_VERSION;
}

static bool state_is_valid(const firmware_ota_state_t *state)
{
    if (state == NULL || state->magic != FIRMWARE_STATE_MAGIC ||
        state->state_version != FIRMWARE_STATE_VERSION || state->pending > 1 ||
        state->report_status > FIRMWARE_OTA_REPORT_ROLLED_BACK) {
        return false;
    }
    if (state->pending && (!is_uuid(state->pending_job_id) ||
                           !is_version(state->pending_version) ||
                           !is_sha256(state->pending_sha256))) {
        return false;
    }
    return state->report_status == FIRMWARE_OTA_REPORT_NONE ||
           (is_uuid(state->report_job_id) && is_version(state->report_version) &&
            is_sha256(state->report_sha256));
}

static esp_err_t save_state_locked(void)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open(FIRMWARE_NAMESPACE, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }
    err = nvs_set_blob(handle, FIRMWARE_STATE_KEY, &s_state, sizeof(s_state));
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

static void pending_to_report_locked(firmware_ota_report_status_t status)
{
    s_state.report_status = (uint8_t)status;
    memcpy(s_state.report_job_id, s_state.pending_job_id, sizeof(s_state.report_job_id));
    memcpy(s_state.report_version, s_state.pending_version, sizeof(s_state.report_version));
    memcpy(s_state.report_sha256, s_state.pending_sha256, sizeof(s_state.report_sha256));
    s_state.pending = 0;
    memset(s_state.pending_job_id, 0, sizeof(s_state.pending_job_id));
    memset(s_state.pending_version, 0, sizeof(s_state.pending_version));
    memset(s_state.pending_sha256, 0, sizeof(s_state.pending_sha256));
}

esp_err_t firmware_ota_init(void)
{
    if (s_mutex != NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    s_mutex = xSemaphoreCreateMutex();
    if (s_mutex == NULL) {
        return ESP_ERR_NO_MEM;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open(FIRMWARE_NAMESPACE, NVS_READONLY, &handle);
    if (err == ESP_OK) {
        size_t size = sizeof(s_state);
        err = nvs_get_blob(handle, FIRMWARE_STATE_KEY, &s_state, &size);
        nvs_close(handle);
        if (err == ESP_OK && (size != sizeof(s_state) || !state_is_valid(&s_state))) {
            err = ESP_ERR_INVALID_STATE;
        }
    }
    if (err == ESP_ERR_NVS_NOT_FOUND || err == ESP_ERR_NVS_INVALID_HANDLE ||
        err == ESP_ERR_INVALID_STATE) {
        reset_state();
        err = save_state_locked();
    }
    if (err != ESP_OK) {
        reset_state();
        return err;
    }

    const esp_app_desc_t *description = esp_app_get_description();
    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t image_state = ESP_OTA_IMG_UNDEFINED;
    esp_err_t state_err = running == NULL ? ESP_ERR_NOT_FOUND
                                          : esp_ota_get_state_partition(running, &image_state);
    if (s_state.pending && description != NULL &&
        strcmp(description->version, s_state.pending_version) != 0) {
        pending_to_report_locked(FIRMWARE_OTA_REPORT_ROLLED_BACK);
        err = save_state_locked();
        ESP_LOGW(TAG, "Pending firmware did not remain active; rollback recorded");
    } else if (s_state.pending && description != NULL &&
               strcmp(description->version, s_state.pending_version) == 0 &&
               state_err == ESP_OK && image_state == ESP_OTA_IMG_VALID) {
        pending_to_report_locked(FIRMWARE_OTA_REPORT_INSTALLED);
        err = save_state_locked();
    }
    s_initialized = err == ESP_OK;
    return err;
}

bool firmware_ota_is_pending(void)
{
    bool pending = false;
    if (s_initialized && s_mutex != NULL && xSemaphoreTake(s_mutex, portMAX_DELAY) == pdTRUE) {
        pending = s_state.pending != 0;
        xSemaphoreGive(s_mutex);
    }
    return pending;
}

esp_err_t firmware_ota_confirm_active(void)
{
    if (!s_initialized || s_mutex == NULL || xSemaphoreTake(s_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    esp_err_t err = ESP_ERR_INVALID_STATE;
    const esp_app_desc_t *description = esp_app_get_description();
    if (s_state.pending && description != NULL &&
        strcmp(description->version, s_state.pending_version) == 0) {
        err = esp_ota_mark_app_valid_cancel_rollback();
        if (err == ESP_OK) {
            firmware_ota_state_t previous = s_state;
            pending_to_report_locked(FIRMWARE_OTA_REPORT_INSTALLED);
            err = save_state_locked();
            if (err != ESP_OK) {
                s_state = previous;
            }
        }
    }
    xSemaphoreGive(s_mutex);
    return err;
}

static void health_guard_task(void *argument)
{
    (void)argument;
    vTaskDelay(pdMS_TO_TICKS(FIRMWARE_HEALTH_TIMEOUT_MS));
    if (firmware_ota_is_pending()) {
        ESP_LOGE(TAG, "Firmware health confirmation timed out; restarting for bootloader rollback");
        esp_restart();
    }
    vTaskDelete(NULL);
}

esp_err_t firmware_ota_start_health_guard(void)
{
    if (!firmware_ota_is_pending()) {
        return ESP_OK;
    }
    return xTaskCreate(health_guard_task, "firmware_guard", FIRMWARE_HEALTH_TASK_STACK_SIZE,
                       NULL, 5, NULL) == pdPASS ? ESP_OK : ESP_ERR_NO_MEM;
}

static void digest_to_hex(const unsigned char digest[32], char output[FIRMWARE_OTA_SHA256_SIZE])
{
    static const char digits[] = "0123456789abcdef";
    for (size_t index = 0; index < 32; ++index) {
        output[index * 2] = digits[digest[index] >> 4];
        output[index * 2 + 1] = digits[digest[index] & 0x0f];
    }
    output[64] = '\0';
}

static esp_err_t download_image(const device_identity_t *identity,
                                const firmware_ota_request_t *request,
                                const esp_partition_t *target)
{
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 112] = {0};
    if (!device_endpoint_build_firmware_update_url(identity->server_base_url, request->job_id,
                                                   url, sizeof(url))) {
        return ESP_ERR_INVALID_ARG;
    }
    esp_http_client_config_t config = {
        .url = url,
        .timeout_ms = 120000,
        .buffer_size = FIRMWARE_DOWNLOAD_BUFFER_SIZE,
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
        err = esp_http_client_set_header(client, "Accept", "application/vnd.stackchan.firmware");
    }
    if (err == ESP_OK) {
        err = esp_http_client_open(client, 0);
    }
    int64_t content_length = err == ESP_OK ? esp_http_client_fetch_headers(client) : -1;
    int status = err == ESP_OK ? esp_http_client_get_status_code(client) : 0;
    if (err != ESP_OK || status != 200 || content_length != (int64_t)request->artifact_size) {
        err = err == ESP_OK ? ESP_ERR_INVALID_RESPONSE : err;
        goto cleanup_client;
    }

    esp_ota_handle_t ota_handle = 0;
    err = esp_ota_begin(target, request->artifact_size, &ota_handle);
    if (err != ESP_OK) {
        goto cleanup_client;
    }
    uint8_t *buffer = malloc(FIRMWARE_DOWNLOAD_BUFFER_SIZE);
    if (buffer == NULL) {
        esp_ota_abort(ota_handle);
        err = ESP_ERR_NO_MEM;
        goto cleanup_client;
    }
    mbedtls_sha256_context sha256;
    mbedtls_sha256_init(&sha256);
    if (mbedtls_sha256_starts(&sha256, 0) != 0) {
        free(buffer);
        mbedtls_sha256_free(&sha256);
        esp_ota_abort(ota_handle);
        err = ESP_FAIL;
        goto cleanup_client;
    }
    size_t offset = 0;
    while (offset < request->artifact_size) {
        size_t remaining = request->artifact_size - offset;
        int wanted = (int)(remaining < FIRMWARE_DOWNLOAD_BUFFER_SIZE
                               ? remaining : FIRMWARE_DOWNLOAD_BUFFER_SIZE);
        int received = esp_http_client_read(client, (char *)buffer, wanted);
        if (received <= 0 || esp_ota_write(ota_handle, buffer, (size_t)received) != ESP_OK ||
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
    char actual_sha256[FIRMWARE_OTA_SHA256_SIZE] = {0};
    digest_to_hex(digest, actual_sha256);
    if (err == ESP_OK && (offset != request->artifact_size ||
                          strcmp(actual_sha256, request->sha256) != 0)) {
        err = ESP_ERR_INVALID_CRC;
    }
    if (err == ESP_OK) {
        err = esp_ota_end(ota_handle);
    } else {
        esp_ota_abort(ota_handle);
    }

cleanup_client:
    (void)esp_http_client_close(client);
    (void)esp_http_client_cleanup(client);
    memset(authorization, 0, sizeof(authorization));
    memset(url, 0, sizeof(url));
    return err;
}

esp_err_t firmware_ota_install(const device_identity_t *identity,
                               const firmware_ota_request_t *request)
{
    if (!device_identity_is_valid(identity) || request == NULL || !is_uuid(request->job_id) ||
        !is_version(request->version) || !is_sha256(request->sha256) ||
        request->artifact_size < 256 || request->artifact_size > FIRMWARE_MAX_ARTIFACT_SIZE ||
        !s_initialized || s_mutex == NULL || firmware_ota_is_pending()) {
        return ESP_ERR_INVALID_ARG;
    }
    const esp_partition_t *running = esp_ota_get_running_partition();
    const esp_partition_t *target = esp_ota_get_next_update_partition(running);
    if (running == NULL || target == NULL || request->artifact_size > target->size) {
        return ESP_ERR_NOT_FOUND;
    }
    esp_err_t err = download_image(identity, request, target);
    if (err != ESP_OK) {
        return err;
    }
    esp_app_desc_t description = {0};
    err = esp_ota_get_partition_description(target, &description);
    if (err != ESP_OK || strcmp(description.project_name, "stackchan_firmware") != 0 ||
        strcmp(description.version, request->version) != 0) {
        return ESP_ERR_INVALID_RESPONSE;
    }
    if (xSemaphoreTake(s_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    firmware_ota_state_t previous = s_state;
    s_state.pending = 1;
    s_state.report_status = FIRMWARE_OTA_REPORT_NONE;
    memcpy(s_state.pending_job_id, request->job_id, sizeof(s_state.pending_job_id));
    memcpy(s_state.pending_version, request->version, sizeof(s_state.pending_version));
    memcpy(s_state.pending_sha256, request->sha256, sizeof(s_state.pending_sha256));
    err = save_state_locked();
    if (err == ESP_OK) {
        err = esp_ota_set_boot_partition(target);
    }
    if (err != ESP_OK) {
        s_state = previous;
        (void)save_state_locked();
    }
    xSemaphoreGive(s_mutex);
    return err;
}

bool firmware_ota_get_report(firmware_ota_report_t *report)
{
    if (report == NULL || !s_initialized || s_mutex == NULL ||
        xSemaphoreTake(s_mutex, portMAX_DELAY) != pdTRUE) {
        return false;
    }
    bool available = s_state.report_status != FIRMWARE_OTA_REPORT_NONE;
    if (available) {
        memset(report, 0, sizeof(*report));
        report->status = (firmware_ota_report_status_t)s_state.report_status;
        memcpy(report->job_id, s_state.report_job_id, sizeof(report->job_id));
        memcpy(report->version, s_state.report_version, sizeof(report->version));
        memcpy(report->sha256, s_state.report_sha256, sizeof(report->sha256));
    }
    xSemaphoreGive(s_mutex);
    return available;
}
