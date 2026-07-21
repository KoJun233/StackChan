#include "esp_err.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "companion_hardware.h"
#include "device_identity.h"
#include "device_provisioning.h"
#include "device_transport.h"
#include "safety_state.h"
#include "voice_control.h"

#if CONFIG_STACKCHAN_PROTOCOL_TESTS
#include "unity.h"
#endif

static const char *TAG = "stackchan";

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
    }
    esp_err_t nvs_err = device_identity_init_encrypted_nvs();
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    ESP_LOGW(TAG, "LAN HTTP development firmware active; credentials are not protected by TLS");
#endif
    if (nvs_err != ESP_OK) {
        ESP_LOGE(TAG, "Encrypted NVS initialization failed: %s", esp_err_to_name(nvs_err));
        return;
    }

    if (hardware_err == ESP_OK) {
        esp_err_t voice_err = voice_control_start();
        if (voice_err != ESP_OK) {
            ESP_LOGE(TAG, "Voice control task did not start: %s", esp_err_to_name(voice_err));
        }
    }
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
#endif
}
