#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"

#include "device_identity.h"

#define FIRMWARE_OTA_JOB_ID_SIZE 37
#define FIRMWARE_OTA_VERSION_SIZE 33
#define FIRMWARE_OTA_SHA256_SIZE 65

typedef struct {
    char job_id[FIRMWARE_OTA_JOB_ID_SIZE];
    char version[FIRMWARE_OTA_VERSION_SIZE];
    char sha256[FIRMWARE_OTA_SHA256_SIZE];
    size_t artifact_size;
} firmware_ota_request_t;

typedef enum {
    FIRMWARE_OTA_REPORT_NONE = 0,
    FIRMWARE_OTA_REPORT_INSTALLED,
    FIRMWARE_OTA_REPORT_ROLLED_BACK,
} firmware_ota_report_status_t;

typedef struct {
    firmware_ota_report_status_t status;
    char job_id[FIRMWARE_OTA_JOB_ID_SIZE];
    char version[FIRMWARE_OTA_VERSION_SIZE];
    char sha256[FIRMWARE_OTA_SHA256_SIZE];
} firmware_ota_report_t;

esp_err_t firmware_ota_init(void);
bool firmware_ota_is_pending(void);
esp_err_t firmware_ota_confirm_active(void);
esp_err_t firmware_ota_start_health_guard(void);
esp_err_t firmware_ota_install(const device_identity_t *identity,
                               const firmware_ota_request_t *request);
bool firmware_ota_get_report(firmware_ota_report_t *report);
