#include "esp_err.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "companion_hardware.h"
#include "device_identity.h"
#include "device_provisioning.h"
#include "device_transport.h"
#include "expression_pack.h"
#include "firmware_ota.h"
#include "safety_state.h"
#include "voice_control.h"
#include "wake_model_ota.h"

#if CONFIG_STACKCHAN_PROTOCOL_TESTS
#include "unity.h"
#endif

static const char *TAG = "stackchan";

static void log_startup_headroom(const char *stage)
{
    ESP_LOGI(TAG, "Startup headroom after %s: main_stack_free=%u internal_heap_free=%u",
             stage,
             (unsigned)uxTaskGetStackHighWaterMark(NULL),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT));
}

#if CONFIG_STACKCHAN_PROTOCOL_TESTS
static void protocol_test_task(void *argument)
{
    (void)argument;
    unity_run_menu();
}
#endif

void app_main(void)
{
    safety_state_init();
    ESP_LOGI(TAG, "StackChan foundation started with safety_state=%s",
             safety_state_name(safety_state_current()));
#if CONFIG_STACKCHAN_PROTOCOL_TESTS
    xTaskCreate(protocol_test_task, "protocol_tests", 4096, NULL, 5, NULL);
    return;
#else
    esp_err_t hardware_err = companion_hardware_init();
    if (hardware_err != ESP_OK) {
        ESP_LOGE(TAG, "CoreS3 hardware did not initialize: %s", esp_err_to_name(hardware_err));
    } else {
        log_startup_headroom("hardware");
    }
    esp_err_t nvs_err = device_identity_init_encrypted_nvs();
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    ESP_LOGW(TAG, "LAN HTTP development firmware active; credentials are not protected by TLS");
#endif
    if (nvs_err != ESP_OK) {
        ESP_LOGE(TAG, "Encrypted NVS initialization failed: %s", esp_err_to_name(nvs_err));
        return;
    }
    esp_err_t expression_err = expression_pack_init();
    if (expression_err != ESP_OK) {
        ESP_LOGW(TAG, "Expression package unavailable; built-in face remains active: %s",
                 esp_err_to_name(expression_err));
    } else if (hardware_err == ESP_OK) {
        companion_hardware_refresh_face();
    }
    esp_err_t firmware_ota_err = firmware_ota_init();
    if (firmware_ota_err != ESP_OK) {
        ESP_LOGE(TAG, "Firmware OTA state initialization failed: %s", esp_err_to_name(firmware_ota_err));
    } else {
        esp_err_t guard_err = firmware_ota_start_health_guard();
        if (guard_err != ESP_OK) {
            ESP_LOGE(TAG, "Firmware OTA health guard did not start: %s", esp_err_to_name(guard_err));
            if (firmware_ota_is_pending()) {
                esp_restart();
            }
        }
    }
    esp_err_t wake_model_err = wake_model_ota_init();
    if (wake_model_err != ESP_OK) {
        ESP_LOGE(TAG, "Wake model OTA state initialization failed: %s", esp_err_to_name(wake_model_err));
    }

    if (hardware_err == ESP_OK) {
        esp_err_t voice_err = voice_control_start();
        if (voice_err != ESP_OK) {
            ESP_LOGE(TAG, "Voice control task did not start: %s", esp_err_to_name(voice_err));
        }
    }
    log_startup_headroom("voice");
    esp_err_t transport_err = device_transport_start();
    if (transport_err != ESP_OK) {
        ESP_LOGE(TAG, "Transport task did not start: %s", esp_err_to_name(transport_err));
        safety_state_stop_motion();
        return;
    }
    esp_err_t provisioning_err = device_provisioning_start();
    if (provisioning_err != ESP_OK) {
        ESP_LOGE(TAG, "Provisioning task did not start: %s", esp_err_to_name(provisioning_err));
        safety_state_stop_motion();
    }
    if (firmware_ota_err == ESP_OK && firmware_ota_is_pending() && hardware_err == ESP_OK &&
        wake_model_err == ESP_OK && transport_err == ESP_OK && provisioning_err == ESP_OK) {
        esp_err_t confirm_err = firmware_ota_confirm_active();
        if (confirm_err != ESP_OK) {
            ESP_LOGE(TAG, "Firmware OTA health confirmation failed: %s", esp_err_to_name(confirm_err));
        }
    }
    if (wake_model_err == ESP_OK) {
        esp_err_t guard_err = wake_model_ota_start_health_guard();
        if (guard_err != ESP_OK) {
            ESP_LOGE(TAG, "Wake model health guard did not start: %s", esp_err_to_name(guard_err));
            if (wake_model_ota_is_pending()) {
                wake_model_ota_rollback_and_restart();
            }
        }
    }
#endif
}
