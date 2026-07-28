#include "expression_pack.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "esp_heap_caps.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_partition.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "mbedtls/sha256.h"
#include "nvs.h"

#include "device_endpoint.h"

#define EXPRESSION_STATE_MAGIC 0x4558504bU
#define EXPRESSION_STATE_VERSION 1U
#define EXPRESSION_NAMESPACE "expr_pack"
#define EXPRESSION_STATE_KEY "state"
#define EXPRESSION_SLOT_NONE 0U
#define EXPRESSION_SLOT_A 1U
#define EXPRESSION_SLOT_B 2U
#define EXPRESSION_HEADER_SIZE 16U
#define EXPRESSION_MAX_MANIFEST_SIZE (16U * 1024U)
#define EXPRESSION_MAX_IMAGE_SIZE (384U * 1024U)
#define EXPRESSION_DOWNLOAD_BUFFER_SIZE 4096U
#define EXPRESSION_STATE_COUNT 8U

typedef struct {
    uint32_t offset;
    uint32_t length;
    char sha256[EXPRESSION_PACK_SHA256_SIZE];
} expression_entry_t;

typedef struct {
    uint32_t magic;
    uint8_t version;
    uint8_t active_slot;
    uint8_t reserved[2];
    uint32_t artifact_size;
    char pack_id[EXPRESSION_PACK_ID_SIZE];
    char sha256[EXPRESSION_PACK_SHA256_SIZE];
} expression_state_t;

static const char *TAG = "expression_pack";
static const char *const SLOT_LABELS[] = {NULL, "expression_a", "expression_b"};
static const char *const STATE_NAMES[] = {
    "idle", "listening", "processing", "speaking", "success", "no_speech", "offline", "error"
};
static const uint8_t PACKAGE_MAGIC[] = {'S', 'C', 'E', 'P', 'K', 'G', '1', 0};
static const uint8_t PNG_MAGIC[] = {0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};

static expression_state_t s_state;
static expression_entry_t s_entries[EXPRESSION_STATE_COUNT];
static SemaphoreHandle_t s_mutex;
static bool s_initialized;
static bool s_active_valid;

static uint32_t read_u32_le(const uint8_t *data)
{
    return (uint32_t)data[0] | ((uint32_t)data[1] << 8) |
           ((uint32_t)data[2] << 16) | ((uint32_t)data[3] << 24);
}

static uint32_t read_u32_be(const uint8_t *data)
{
    return ((uint32_t)data[0] << 24) | ((uint32_t)data[1] << 16) |
           ((uint32_t)data[2] << 8) | (uint32_t)data[3];
}

static bool is_hex_string(const char *value, size_t length)
{
    if (value == NULL || strlen(value) != length) {
        return false;
    }
    for (size_t index = 0; index < length; index++) {
        if (!isxdigit((unsigned char)value[index]) || isupper((unsigned char)value[index])) {
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
    for (size_t index = 0; index < 36; index++) {
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
    memset(s_entries, 0, sizeof(s_entries));
    s_state.magic = EXPRESSION_STATE_MAGIC;
    s_state.version = EXPRESSION_STATE_VERSION;
    s_active_valid = false;
}

static bool state_is_valid(const expression_state_t *state)
{
    if (state == NULL || state->magic != EXPRESSION_STATE_MAGIC ||
        state->version != EXPRESSION_STATE_VERSION || state->active_slot > EXPRESSION_SLOT_B) {
        return false;
    }
    return state->active_slot == EXPRESSION_SLOT_NONE ||
           (state->artifact_size > EXPRESSION_HEADER_SIZE &&
            state->artifact_size <= EXPRESSION_PACK_MAX_ARTIFACT_SIZE &&
            is_uuid(state->pack_id) && is_hex_string(state->sha256, 64));
}

static esp_err_t save_state_locked(void)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open(EXPRESSION_NAMESPACE, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }
    err = nvs_set_blob(handle, EXPRESSION_STATE_KEY, &s_state, sizeof(s_state));
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

static esp_err_t erase_slot(uint8_t slot)
{
    if (slot != EXPRESSION_SLOT_A && slot != EXPRESSION_SLOT_B) {
        return ESP_ERR_INVALID_ARG;
    }
    const esp_partition_t *partition = esp_partition_find_first(
        ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_ANY, SLOT_LABELS[slot]);
    if (partition == NULL) {
        return ESP_ERR_NOT_FOUND;
    }
    return esp_partition_erase_range(partition, 0, partition->size);
}

static void digest_to_hex(const unsigned char digest[32], char output[EXPRESSION_PACK_SHA256_SIZE])
{
    static const char digits[] = "0123456789abcdef";
    for (size_t index = 0; index < 32; index++) {
        output[index * 2] = digits[digest[index] >> 4];
        output[index * 2 + 1] = digits[digest[index] & 0x0f];
    }
    output[64] = '\0';
}

static bool partition_digest(const esp_partition_t *partition,
                             size_t offset,
                             size_t length,
                             char output[EXPRESSION_PACK_SHA256_SIZE])
{
    uint8_t *buffer = malloc(EXPRESSION_DOWNLOAD_BUFFER_SIZE);
    if (buffer == NULL) {
        return false;
    }
    mbedtls_sha256_context context;
    mbedtls_sha256_init(&context);
    bool valid = mbedtls_sha256_starts(&context, 0) == 0;
    size_t consumed = 0;
    while (valid && consumed < length) {
        size_t chunk = length - consumed;
        if (chunk > EXPRESSION_DOWNLOAD_BUFFER_SIZE) {
            chunk = EXPRESSION_DOWNLOAD_BUFFER_SIZE;
        }
        valid = esp_partition_read(partition, offset + consumed, buffer, chunk) == ESP_OK &&
                mbedtls_sha256_update(&context, buffer, chunk) == 0;
        consumed += chunk;
    }
    unsigned char digest[32] = {0};
    valid = valid && mbedtls_sha256_finish(&context, digest) == 0;
    mbedtls_sha256_free(&context);
    free(buffer);
    if (valid) {
        digest_to_hex(digest, output);
    }
    return valid;
}

static int state_index(const char *name)
{
    for (int index = 0; index < (int)EXPRESSION_STATE_COUNT; index++) {
        if (strcmp(name, STATE_NAMES[index]) == 0) {
            return index;
        }
    }
    return -1;
}

static bool json_integer(cJSON *node, int minimum, int maximum)
{
    return cJSON_IsNumber(node) && node->valuedouble == (double)node->valueint &&
           node->valueint >= minimum && node->valueint <= maximum;
}

static bool validate_png_header(const esp_partition_t *partition, size_t offset, size_t length)
{
    uint8_t header[33] = {0};
    if (length < sizeof(header) || esp_partition_read(partition, offset, header, sizeof(header)) != ESP_OK ||
        memcmp(header, PNG_MAGIC, sizeof(PNG_MAGIC)) != 0 || memcmp(header + 12, "IHDR", 4) != 0 ||
        read_u32_be(header + 16) != 320 || read_u32_be(header + 20) != 240 || header[24] != 8) {
        return false;
    }
    return header[25] == 2 || header[25] == 3 || header[25] == 6;
}

static bool validate_package(const esp_partition_t *partition,
                             size_t artifact_size,
                             const char *expected_sha256,
                             expression_entry_t output[EXPRESSION_STATE_COUNT])
{
    uint8_t header[EXPRESSION_HEADER_SIZE] = {0};
    if (partition == NULL || artifact_size <= EXPRESSION_HEADER_SIZE || artifact_size > partition->size ||
        esp_partition_read(partition, 0, header, sizeof(header)) != ESP_OK ||
        memcmp(header, PACKAGE_MAGIC, sizeof(PACKAGE_MAGIC)) != 0 || read_u32_le(header + 8) != 1) {
        return false;
    }
    uint32_t manifest_size = read_u32_le(header + 12);
    if (manifest_size == 0 || manifest_size > EXPRESSION_MAX_MANIFEST_SIZE ||
        EXPRESSION_HEADER_SIZE + manifest_size >= artifact_size) {
        return false;
    }
    char package_sha256[EXPRESSION_PACK_SHA256_SIZE] = {0};
    if (!partition_digest(partition, 0, artifact_size, package_sha256) ||
        strcmp(package_sha256, expected_sha256) != 0) {
        return false;
    }

    char *manifest = malloc((size_t)manifest_size + 1);
    if (manifest == NULL) {
        return false;
    }
    bool valid = esp_partition_read(partition, EXPRESSION_HEADER_SIZE, manifest, manifest_size) == ESP_OK;
    manifest[manifest_size] = '\0';
    cJSON *root = valid ? cJSON_ParseWithLength(manifest, manifest_size) : NULL;
    free(manifest);
    valid = root != NULL && cJSON_IsObject(root) && cJSON_GetArraySize(root) == 4 &&
            json_integer(cJSON_GetObjectItemCaseSensitive(root, "version"), 1, 1) &&
            json_integer(cJSON_GetObjectItemCaseSensitive(root, "width"), 320, 320) &&
            json_integer(cJSON_GetObjectItemCaseSensitive(root, "height"), 240, 240);
    cJSON *states = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root, "states");
    valid = valid && cJSON_IsArray(states) && cJSON_GetArraySize(states) == EXPRESSION_STATE_COUNT;
    bool seen[EXPRESSION_STATE_COUNT] = {false};
    uint32_t payload_start = EXPRESSION_HEADER_SIZE + manifest_size;
    uint32_t expected_payload_offset = 0;
    int expected_state_index = 0;
    cJSON *entry = NULL;
    cJSON_ArrayForEach(entry, states) {
        if (!valid || !cJSON_IsObject(entry) || cJSON_GetArraySize(entry) != 7) {
            valid = false;
            break;
        }
        cJSON *name = cJSON_GetObjectItemCaseSensitive(entry, "state");
        cJSON *format = cJSON_GetObjectItemCaseSensitive(entry, "format");
        cJSON *width = cJSON_GetObjectItemCaseSensitive(entry, "width");
        cJSON *height = cJSON_GetObjectItemCaseSensitive(entry, "height");
        cJSON *offset = cJSON_GetObjectItemCaseSensitive(entry, "offset");
        cJSON *length = cJSON_GetObjectItemCaseSensitive(entry, "length");
        cJSON *sha256 = cJSON_GetObjectItemCaseSensitive(entry, "sha256");
        int index = cJSON_IsString(name) && name->valuestring != NULL ? state_index(name->valuestring) : -1;
        valid = index == expected_state_index && !seen[index] && cJSON_IsString(format) &&
                strcmp(format->valuestring, "png") == 0 && json_integer(width, 320, 320) &&
                json_integer(height, 240, 240) && json_integer(offset, 0, EXPRESSION_PACK_MAX_ARTIFACT_SIZE) &&
                json_integer(length, 33, EXPRESSION_MAX_IMAGE_SIZE) && cJSON_IsString(sha256) &&
                sha256->valuestring != NULL && is_hex_string(sha256->valuestring, 64) &&
                (uint32_t)offset->valueint == expected_payload_offset;
        uint32_t absolute_offset = valid ? payload_start + (uint32_t)offset->valueint : 0;
        uint32_t image_length = valid ? (uint32_t)length->valueint : 0;
        valid = valid && absolute_offset >= payload_start && absolute_offset <= artifact_size &&
                image_length <= artifact_size - absolute_offset &&
                validate_png_header(partition, absolute_offset, image_length);
        char actual_sha256[EXPRESSION_PACK_SHA256_SIZE] = {0};
        valid = valid && partition_digest(partition, absolute_offset, image_length, actual_sha256) &&
                strcmp(actual_sha256, sha256->valuestring) == 0;
        if (valid) {
            seen[index] = true;
            output[index].offset = absolute_offset;
            output[index].length = image_length;
            memcpy(output[index].sha256, sha256->valuestring, EXPRESSION_PACK_SHA256_SIZE);
            expected_payload_offset += image_length;
            expected_state_index++;
        }
    }
    for (size_t index = 0; valid && index < EXPRESSION_STATE_COUNT; index++) {
        valid = seen[index];
    }
    valid = valid && payload_start + expected_payload_offset == artifact_size;
    cJSON_Delete(root);
    return valid;
}

esp_err_t expression_pack_init(void)
{
    if (s_mutex != NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    s_mutex = xSemaphoreCreateMutex();
    if (s_mutex == NULL) {
        return ESP_ERR_NO_MEM;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open(EXPRESSION_NAMESPACE, NVS_READONLY, &handle);
    if (err == ESP_OK) {
        size_t size = sizeof(s_state);
        err = nvs_get_blob(handle, EXPRESSION_STATE_KEY, &s_state, &size);
        nvs_close(handle);
        if (err == ESP_OK && (size != sizeof(s_state) || !state_is_valid(&s_state))) {
            err = ESP_ERR_INVALID_STATE;
        }
    }
    if (err == ESP_ERR_NVS_NOT_FOUND || err == ESP_ERR_NVS_INVALID_HANDLE || err == ESP_ERR_INVALID_STATE) {
        reset_state();
        err = save_state_locked();
    }
    if (err != ESP_OK) {
        reset_state();
        return err;
    }
    if (s_state.active_slot != EXPRESSION_SLOT_NONE) {
        const esp_partition_t *partition = esp_partition_find_first(
            ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_ANY, SLOT_LABELS[s_state.active_slot]);
        s_active_valid = validate_package(
            partition, s_state.artifact_size, s_state.sha256, s_entries);
        if (!s_active_valid) {
            ESP_LOGW(TAG, "Active expression package is invalid; built-in face restored");
            reset_state();
            err = save_state_locked();
        }
    }
    s_initialized = err == ESP_OK;
    return err;
}

bool expression_pack_is_active(void)
{
    bool active = false;
    if (s_initialized && s_mutex != NULL && xSemaphoreTake(s_mutex, portMAX_DELAY) == pdTRUE) {
        active = s_active_valid && s_state.active_slot != EXPRESSION_SLOT_NONE;
        xSemaphoreGive(s_mutex);
    }
    return active;
}

static esp_err_t download_to_partition(const device_identity_t *identity,
                                       const expression_pack_request_t *request,
                                       const esp_partition_t *partition)
{
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 112] = {0};
    if (!device_endpoint_build_expression_pack_url(
            identity->server_base_url, request->pack_id, url, sizeof(url))) {
        return ESP_ERR_INVALID_ARG;
    }
    esp_http_client_config_t config = {
        .url = url,
        .timeout_ms = 60000,
        .buffer_size = EXPRESSION_DOWNLOAD_BUFFER_SIZE,
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
        err = esp_http_client_set_header(client, "Accept", "application/vnd.stackchan.expression-pack");
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
    uint8_t *buffer = malloc(EXPRESSION_DOWNLOAD_BUFFER_SIZE);
    if (buffer == NULL) {
        err = ESP_ERR_NO_MEM;
        goto cleanup;
    }
    size_t offset = 0;
    while (offset < request->artifact_size) {
        size_t remaining = request->artifact_size - offset;
        int requested = (int)(remaining < EXPRESSION_DOWNLOAD_BUFFER_SIZE
                                  ? remaining : EXPRESSION_DOWNLOAD_BUFFER_SIZE);
        int received = esp_http_client_read(client, (char *)buffer, requested);
        if (received <= 0 || esp_partition_write(partition, offset, buffer, (size_t)received) != ESP_OK) {
            err = ESP_FAIL;
            break;
        }
        offset += (size_t)received;
    }
    free(buffer);
    if (err == ESP_OK && offset != request->artifact_size) {
        err = ESP_ERR_INVALID_SIZE;
    }

cleanup:
    (void)esp_http_client_close(client);
    (void)esp_http_client_cleanup(client);
    memset(authorization, 0, sizeof(authorization));
    memset(url, 0, sizeof(url));
    return err;
}

esp_err_t expression_pack_install(const device_identity_t *identity,
                                  const expression_pack_request_t *request)
{
    if (!device_identity_is_valid(identity) || request == NULL || !is_uuid(request->pack_id) ||
        !is_hex_string(request->sha256, 64) || request->artifact_size <= EXPRESSION_HEADER_SIZE ||
        request->artifact_size > EXPRESSION_PACK_MAX_ARTIFACT_SIZE || !s_initialized || s_mutex == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    if (xSemaphoreTake(s_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    if (s_active_valid && strcmp(s_state.pack_id, request->pack_id) == 0 &&
        strcmp(s_state.sha256, request->sha256) == 0) {
        uint8_t inactive_slot = s_state.active_slot == EXPRESSION_SLOT_A
                                    ? EXPRESSION_SLOT_B : EXPRESSION_SLOT_A;
        esp_err_t cleanup_err = erase_slot(inactive_slot);
        if (cleanup_err != ESP_OK) {
            ESP_LOGW(TAG, "Unable to erase inactive expression slot: %s",
                     esp_err_to_name(cleanup_err));
        }
        xSemaphoreGive(s_mutex);
        return ESP_OK;
    }
    uint8_t target_slot = s_state.active_slot == EXPRESSION_SLOT_A
                              ? EXPRESSION_SLOT_B : EXPRESSION_SLOT_A;
    xSemaphoreGive(s_mutex);
    const esp_partition_t *partition = esp_partition_find_first(
        ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_ANY, SLOT_LABELS[target_slot]);
    if (partition == NULL || request->artifact_size > partition->size) {
        return ESP_ERR_NOT_FOUND;
    }
    esp_err_t err = download_to_partition(identity, request, partition);
    expression_entry_t entries[EXPRESSION_STATE_COUNT] = {0};
    if (err == ESP_OK && !validate_package(
            partition, request->artifact_size, request->sha256, entries)) {
        err = ESP_ERR_INVALID_RESPONSE;
    }
    if (err != ESP_OK || xSemaphoreTake(s_mutex, portMAX_DELAY) != pdTRUE) {
        return err == ESP_OK ? ESP_ERR_INVALID_STATE : err;
    }
    expression_state_t previous = s_state;
    expression_entry_t previous_entries[EXPRESSION_STATE_COUNT];
    memcpy(previous_entries, s_entries, sizeof(previous_entries));
    s_state.active_slot = target_slot;
    s_state.artifact_size = (uint32_t)request->artifact_size;
    memcpy(s_state.pack_id, request->pack_id, sizeof(s_state.pack_id));
    memcpy(s_state.sha256, request->sha256, sizeof(s_state.sha256));
    memcpy(s_entries, entries, sizeof(s_entries));
    s_active_valid = true;
    err = save_state_locked();
    if (err != ESP_OK) {
        s_state = previous;
        memcpy(s_entries, previous_entries, sizeof(s_entries));
        s_active_valid = previous.active_slot != EXPRESSION_SLOT_NONE;
    } else if (previous.active_slot != EXPRESSION_SLOT_NONE &&
               previous.active_slot != target_slot) {
        esp_err_t erase_err = erase_slot(previous.active_slot);
        if (erase_err != ESP_OK) {
            ESP_LOGW(TAG, "Unable to erase inactive expression slot: %s",
                     esp_err_to_name(erase_err));
        }
    }
    xSemaphoreGive(s_mutex);
    return err;
}

esp_err_t expression_pack_clear(void)
{
    if (!s_initialized || s_mutex == NULL || xSemaphoreTake(s_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    if (s_state.active_slot == EXPRESSION_SLOT_NONE && !s_active_valid) {
        xSemaphoreGive(s_mutex);
        return ESP_OK;
    }
    expression_state_t previous = s_state;
    expression_entry_t previous_entries[EXPRESSION_STATE_COUNT];
    memcpy(previous_entries, s_entries, sizeof(previous_entries));
    reset_state();
    esp_err_t err = save_state_locked();
    if (err != ESP_OK) {
        s_state = previous;
        memcpy(s_entries, previous_entries, sizeof(s_entries));
        s_active_valid = previous.active_slot != EXPRESSION_SLOT_NONE;
    }
    if (err == ESP_OK) {
        esp_err_t erase_a_err = erase_slot(EXPRESSION_SLOT_A);
        esp_err_t erase_b_err = erase_slot(EXPRESSION_SLOT_B);
        if (erase_a_err != ESP_OK) {
            err = erase_a_err;
        } else if (erase_b_err != ESP_OK) {
            err = erase_b_err;
        }
    }
    xSemaphoreGive(s_mutex);
    return err;
}

esp_err_t expression_pack_read_state(companion_face_state_t state,
                                     uint8_t **image,
                                     size_t *image_size)
{
    if (image == NULL || image_size == NULL || state < COMPANION_FACE_IDLE ||
        state > COMPANION_FACE_RECOVERABLE_ERROR || !s_initialized || s_mutex == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    *image = NULL;
    *image_size = 0;
    if (xSemaphoreTake(s_mutex, portMAX_DELAY) != pdTRUE) {
        return ESP_ERR_INVALID_STATE;
    }
    if (!s_active_valid || s_state.active_slot == EXPRESSION_SLOT_NONE) {
        xSemaphoreGive(s_mutex);
        return ESP_ERR_NOT_FOUND;
    }
    expression_entry_t entry = s_entries[(int)state];
    const esp_partition_t *partition = esp_partition_find_first(
        ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_ANY, SLOT_LABELS[s_state.active_slot]);
    xSemaphoreGive(s_mutex);
    if (partition == NULL || entry.length == 0 || entry.length > EXPRESSION_MAX_IMAGE_SIZE) {
        return ESP_ERR_NOT_FOUND;
    }
    uint8_t *buffer = heap_caps_malloc(entry.length, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (buffer == NULL) {
        buffer = malloc(entry.length);
    }
    if (buffer == NULL) {
        return ESP_ERR_NO_MEM;
    }
    if (esp_partition_read(partition, entry.offset, buffer, entry.length) != ESP_OK) {
        free(buffer);
        return ESP_FAIL;
    }
    *image = buffer;
    *image_size = entry.length;
    return ESP_OK;
}
