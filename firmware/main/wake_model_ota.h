#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#include "device_identity.h"

#define WAKE_MODEL_OTA_JOB_ID_SIZE 37
#define WAKE_MODEL_OTA_MODEL_NAME_SIZE 32
#define WAKE_MODEL_OTA_SHA256_SIZE 65

typedef struct {
    char job_id[WAKE_MODEL_OTA_JOB_ID_SIZE];
    char model_name[WAKE_MODEL_OTA_MODEL_NAME_SIZE];
    char sha256[WAKE_MODEL_OTA_SHA256_SIZE];
    size_t artifact_size;
} wake_model_ota_request_t;

typedef enum {
    WAKE_MODEL_OTA_REPORT_NONE = 0,
    WAKE_MODEL_OTA_REPORT_INSTALLED,
    WAKE_MODEL_OTA_REPORT_ROLLED_BACK,
} wake_model_ota_report_status_t;

typedef struct {
    wake_model_ota_report_status_t status;
    char job_id[WAKE_MODEL_OTA_JOB_ID_SIZE];
    char model_name[WAKE_MODEL_OTA_MODEL_NAME_SIZE];
    char sha256[WAKE_MODEL_OTA_SHA256_SIZE];
} wake_model_ota_report_t;

/** Loads the selected model slot and performs second-boot rollback when needed. */
esp_err_t wake_model_ota_init(void);

const char *wake_model_ota_active_partition_label(void);
const char *wake_model_ota_active_model_name(void);
bool wake_model_ota_is_pending(void);

/** Confirms that the pending model has created a valid WakeNet listener. */
esp_err_t wake_model_ota_confirm_active(void);

/** Rolls back to the previous slot and restarts. This function does not return. */
void wake_model_ota_rollback_and_restart(void) __attribute__((noreturn));

/** Starts a bounded guard that rolls back an unconfirmed pending model. */
esp_err_t wake_model_ota_start_health_guard(void);

/** Downloads, verifies and stages a model in an inactive OTA slot. */
esp_err_t wake_model_ota_install(
    const device_identity_t *identity,
    const wake_model_ota_request_t *request);

bool wake_model_ota_get_report(wake_model_ota_report_t *report);
