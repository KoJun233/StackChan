#include "device_identity.h"

#include <ctype.h>
#include <stdbool.h>
#include <string.h>

#include "esp_log.h"
#include "lwip/sockets.h"
#include "nvs.h"
#include "nvs_flash.h"

#define IDENTITY_NAMESPACE "identity"
#define SERVER_BASE_URL_KEY "server_base_url"
#define DEVICE_ID_KEY "device_id"
#define ACCESS_TOKEN_KEY "access_token"
#define ACCESS_EXPIRY_KEY "access_expiry"
#define REFRESH_TOKEN_KEY "refresh_token"

static const char *TAG = "device_identity";

static bool has_valid_bounded_length(const char *value, size_t capacity)
{
    return value != NULL && strnlen(value, capacity) > 0 && strnlen(value, capacity) < capacity;
}

static bool is_unreserved_token_character(char character)
{
    return isalnum((unsigned char)character) || character == '-' || character == '.' || character == '_' ||
           character == '~';
}

static bool is_valid_port(const char *start, const char *end)
{
    if (start == end) {
        return false;
    }
    unsigned port = 0;
    for (const char *cursor = start; cursor < end; ++cursor) {
        if (!isdigit((unsigned char)*cursor)) {
            return false;
        }
        unsigned digit = (unsigned)(*cursor - '0');
        if (port > (65535U - digit) / 10U) {
            return false;
        }
        port = port * 10U + digit;
    }
    return port > 0;
}

static bool is_valid_hostname_or_ipv4(const char *start, const char *end)
{
    size_t length = (size_t)(end - start);
    if (length == 0 || length >= DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN) {
        return false;
    }
    bool numeric_dotted = false;
    bool only_digits_and_dots = true;
    size_t label_length = 0;
    for (const char *cursor = start; cursor < end; ++cursor) {
        char character = *cursor;
        if (!(isalnum((unsigned char)character) || character == '-' || character == '.')) {
            return false;
        }
        if (character == '.') {
            numeric_dotted = true;
            if (label_length == 0 || cursor[-1] == '-' || label_length > 63) {
                return false;
            }
            label_length = 0;
        } else {
            if (label_length == 0 && character == '-') {
                return false;
            }
            ++label_length;
        }
        if (!(isdigit((unsigned char)character) || character == '.')) {
            only_digits_and_dots = false;
        }
    }
    if (label_length == 0 || end[-1] == '-' || label_length > 63) {
        return false;
    }
    if (!numeric_dotted || !only_digits_and_dots) {
        return true;
    }
    char address[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN] = {0};
    memcpy(address, start, length);
    struct in_addr parsed = {0};
    return inet_pton(AF_INET, address, &parsed) == 1;
}

static bool is_valid_authority(const char *start, const char *end)
{
    if (start == end) {
        return false;
    }
    if (*start == '[') {
        const char *close = memchr(start + 1, ']', (size_t)(end - start - 1));
        if (close == NULL || close == start + 1) {
            return false;
        }
        size_t address_length = (size_t)(close - start - 1);
        if (address_length >= INET6_ADDRSTRLEN) {
            return false;
        }
        char address[INET6_ADDRSTRLEN] = {0};
        memcpy(address, start + 1, address_length);
        struct in6_addr parsed = {0};
        if (inet_pton(AF_INET6, address, &parsed) != 1) {
            return false;
        }
        if (close + 1 == end) {
            return true;
        }
        return close[1] == ':' && is_valid_port(close + 2, end);
    }

    const char *colon = memchr(start, ':', (size_t)(end - start));
    if (colon != NULL && memchr(colon + 1, ':', (size_t)(end - colon - 1)) != NULL) {
        return false;
    }
    const char *host_end = colon == NULL ? end : colon;
    return is_valid_hostname_or_ipv4(start, host_end) &&
           (colon == NULL || is_valid_port(colon + 1, end));
}

static bool is_valid_https_server_base_url(const char *server_base_url)
{
    if (!has_valid_bounded_length(server_base_url, DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN)) {
        return false;
    }

    if (strncmp(server_base_url, "https://", 8) != 0) {
        return false;
    }
    const char *authority = server_base_url + 8;

    if (*authority == '\0' || strpbrk(authority, "?#@") != NULL) {
        return false;
    }

    const char *path = strchr(authority, '/');
    if (path != NULL && path[1] != '\0') {
        return false;
    }

    for (const char *cursor = server_base_url; *cursor != '\0'; ++cursor) {
        if (iscntrl((unsigned char)*cursor) || isspace((unsigned char)*cursor)) {
            return false;
        }
    }
    const char *authority_end = path == NULL ? server_base_url + strlen(server_base_url) : path;
    return is_valid_authority(authority, authority_end);
}

static bool parse_ipv4_octet(const char **cursor, const char *end, unsigned *octet)
{
    const char *start = *cursor;
    if (start == end || !isdigit((unsigned char)*start)) {
        return false;
    }
    unsigned value = 0;
    while (*cursor < end && isdigit((unsigned char)**cursor)) {
        value = value * 10U + (unsigned)(**cursor - '0');
        if (value > 255U) {
            return false;
        }
        ++*cursor;
    }
    if (*cursor - start > 1 && *start == '0') {
        return false;
    }
    *octet = value;
    return true;
}

static bool is_private_ipv4_authority(const char *start, const char *end)
{
    const char *colon = memchr(start, ':', (size_t)(end - start));
    if (colon != NULL && memchr(colon + 1, ':', (size_t)(end - colon - 1)) != NULL) {
        return false;
    }
    const char *host_end = colon == NULL ? end : colon;
    const char *cursor = start;
    unsigned octets[4] = {0};
    for (size_t index = 0; index < 4; ++index) {
        if (!parse_ipv4_octet(&cursor, host_end, &octets[index])) {
            return false;
        }
        if (index < 3) {
            if (cursor == host_end || *cursor != '.') {
                return false;
            }
            ++cursor;
        }
    }
    if (cursor != host_end) {
        return false;
    }
    bool private_address = octets[0] == 10 ||
                           (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) ||
                           (octets[0] == 192 && octets[1] == 168);
    return private_address && (colon == NULL || is_valid_port(colon + 1, end));
}

static bool is_valid_lan_http_server_base_url(const char *server_base_url)
{
    if (!has_valid_bounded_length(server_base_url, DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN) ||
        strncmp(server_base_url, "http://", 7) != 0) {
        return false;
    }
    const char *authority = server_base_url + 7;
    if (*authority == '\0' || strpbrk(authority, "/?#@") != NULL) {
        return false;
    }
    for (const char *cursor = server_base_url; *cursor != '\0'; ++cursor) {
        if (iscntrl((unsigned char)*cursor) || isspace((unsigned char)*cursor)) {
            return false;
        }
    }
    return is_private_ipv4_authority(authority, server_base_url + strlen(server_base_url));
}

bool device_identity_is_valid_server_base_url(const char *server_base_url)
{
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    return is_valid_lan_http_server_base_url(server_base_url);
#else
    return is_valid_https_server_base_url(server_base_url);
#endif
}

static bool is_valid_device_id(const char *device_id)
{
    if (device_id == NULL || strnlen(device_id, DEVICE_IDENTITY_DEVICE_ID_MAX_LEN) != 36) {
        return false;
    }

    for (size_t index = 0; index < 36; ++index) {
        if (index == 8 || index == 13 || index == 18 || index == 23) {
            if (device_id[index] != '-') {
                return false;
            }
        } else if (!isxdigit((unsigned char)device_id[index])) {
            return false;
        }
    }
    return true;
}

static bool is_valid_token(const char *token, size_t capacity)
{
    if (!has_valid_bounded_length(token, capacity)) {
        return false;
    }

    for (const char *cursor = token; *cursor != '\0'; ++cursor) {
        if (!is_unreserved_token_character(*cursor)) {
            return false;
        }
    }
    return true;
}

static bool is_decimal_at(const char *value, size_t offset, size_t length)
{
    for (size_t index = 0; index < length; ++index) {
        if (!isdigit((unsigned char)value[offset + index])) {
            return false;
        }
    }
    return true;
}

static unsigned parse_decimal(const char *value, size_t offset, size_t length)
{
    unsigned parsed = 0;
    for (size_t index = 0; index < length; ++index) {
        parsed = parsed * 10U + (unsigned)(value[offset + index] - '0');
    }
    return parsed;
}

static bool is_leap_year(unsigned year)
{
    return year % 4U == 0U && (year % 100U != 0U || year % 400U == 0U);
}

static bool is_valid_expiry(const char *expiry)
{
    size_t length = expiry == NULL ? 0 : strnlen(expiry, DEVICE_IDENTITY_EXPIRY_MAX_LEN);
    if (length < 20 || length >= DEVICE_IDENTITY_EXPIRY_MAX_LEN || expiry[length - 1] != 'Z' ||
        expiry[4] != '-' || expiry[7] != '-' || expiry[10] != 'T' || expiry[13] != ':' || expiry[16] != ':' ||
        !is_decimal_at(expiry, 0, 4) || !is_decimal_at(expiry, 5, 2) || !is_decimal_at(expiry, 8, 2) ||
        !is_decimal_at(expiry, 11, 2) || !is_decimal_at(expiry, 14, 2) || !is_decimal_at(expiry, 17, 2)) {
        return false;
    }
    if (length > 20) {
        if (length < 22 || expiry[19] != '.' || length > 30 || !is_decimal_at(expiry, 20, length - 21)) {
            return false;
        }
    }
    unsigned year = parse_decimal(expiry, 0, 4);
    unsigned month = parse_decimal(expiry, 5, 2);
    unsigned day = parse_decimal(expiry, 8, 2);
    unsigned hour = parse_decimal(expiry, 11, 2);
    unsigned minute = parse_decimal(expiry, 14, 2);
    unsigned second = parse_decimal(expiry, 17, 2);
    static const unsigned days_per_month[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    if (year == 0 || month == 0 || month > 12 || day == 0 || hour > 23 || minute > 59 || second > 59) {
        return false;
    }
    unsigned max_day = days_per_month[month - 1];
    if (month == 2 && is_leap_year(year)) {
        max_day = 29;
    }
    return day <= max_day;
}

bool device_identity_is_valid(const device_identity_t *identity)
{
    return identity != NULL && device_identity_is_valid_server_base_url(identity->server_base_url) &&
           is_valid_device_id(identity->device_id) &&
           is_valid_token(identity->access_token, DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN) &&
           is_valid_expiry(identity->access_token_expires_at) &&
           is_valid_token(identity->refresh_token, DEVICE_IDENTITY_REFRESH_TOKEN_MAX_LEN);
}

static esp_err_t get_required_string(nvs_handle_t handle, const char *key, char *value, size_t value_size)
{
    size_t stored_size = value_size;
    esp_err_t err = nvs_get_str(handle, key, value, &stored_size);
    if (err != ESP_OK) {
        return err;
    }
    return stored_size > 0 && stored_size <= value_size ? ESP_OK : ESP_ERR_INVALID_SIZE;
}

esp_err_t device_identity_init_encrypted_nvs(void)
{
#if !CONFIG_NVS_ENCRYPTION
#error "StackChan device identities require CONFIG_NVS_ENCRYPTION"
#endif
    esp_err_t err = nvs_flash_init();
    if (err != ESP_ERR_NVS_NO_FREE_PAGES && err != ESP_ERR_NVS_NEW_VERSION_FOUND) {
        return err;
    }

    ESP_LOGW(TAG, "Recovering encrypted NVS after %s", esp_err_to_name(err));
    err = nvs_flash_erase();
    if (err != ESP_OK) {
        return err;
    }
    return nvs_flash_init();
}

esp_err_t device_identity_load(device_identity_t *identity)
{
    if (identity == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(identity, 0, sizeof(*identity));

    nvs_handle_t handle;
    esp_err_t err = nvs_open(IDENTITY_NAMESPACE, NVS_READONLY, &handle);
    if (err != ESP_OK) {
        return err;
    }

    esp_err_t first_err = get_required_string(handle, SERVER_BASE_URL_KEY, identity->server_base_url,
                                               sizeof(identity->server_base_url));
    err = get_required_string(handle, DEVICE_ID_KEY, identity->device_id, sizeof(identity->device_id));
    first_err = first_err == ESP_OK ? err : first_err;
    err = get_required_string(handle, ACCESS_TOKEN_KEY, identity->access_token, sizeof(identity->access_token));
    first_err = first_err == ESP_OK ? err : first_err;
    err = get_required_string(handle, ACCESS_EXPIRY_KEY, identity->access_token_expires_at,
                              sizeof(identity->access_token_expires_at));
    first_err = first_err == ESP_OK ? err : first_err;
    err = get_required_string(handle, REFRESH_TOKEN_KEY, identity->refresh_token, sizeof(identity->refresh_token));
    first_err = first_err == ESP_OK ? err : first_err;
    nvs_close(handle);

    if (first_err != ESP_OK || !device_identity_is_valid(identity)) {
        memset(identity, 0, sizeof(*identity));
        return first_err == ESP_OK ? ESP_ERR_INVALID_ARG : first_err;
    }
    return ESP_OK;
}

esp_err_t device_identity_save(const device_identity_t *identity)
{
    if (!device_identity_is_valid(identity)) {
        return ESP_ERR_INVALID_ARG;
    }

    nvs_handle_t handle;
    esp_err_t err = nvs_open(IDENTITY_NAMESPACE, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }

    err = nvs_set_str(handle, SERVER_BASE_URL_KEY, identity->server_base_url);
    if (err == ESP_OK) {
        err = nvs_set_str(handle, DEVICE_ID_KEY, identity->device_id);
    }
    if (err == ESP_OK) {
        err = nvs_set_str(handle, ACCESS_TOKEN_KEY, identity->access_token);
    }
    if (err == ESP_OK) {
        err = nvs_set_str(handle, ACCESS_EXPIRY_KEY, identity->access_token_expires_at);
    }
    if (err == ESP_OK) {
        err = nvs_set_str(handle, REFRESH_TOKEN_KEY, identity->refresh_token);
    }
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

esp_err_t device_identity_clear(void)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open(IDENTITY_NAMESPACE, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }

    err = nvs_erase_all(handle);
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}
