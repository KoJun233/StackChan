#include "device_transport.h"

#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "esp_check.h"
#include "esp_app_desc.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "esp_websocket_client.h"
#include "esp_wifi.h"
#include "esp_wifi_default.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"

#include "companion_hardware.h"
#include "device_identity.h"
#include "device_credentials.h"
#include "device_endpoint.h"
#include "device_protocol.h"
#include "expression_pack.h"
#include "firmware_ota.h"
#include "safety_state.h"
#include "voice_control.h"
#include "wake_model_ota.h"

#define WIFI_CONNECTED_BIT BIT0
#define TRANSPORT_TASK_STACK_SIZE 10240
#define TRANSPORT_TASK_PRIORITY 5
#define WEBSOCKET_TASK_STACK_SIZE 8192
#define WEBSOCKET_NETWORK_TIMEOUT_MS 10000
#define TRANSPORT_IDLE_POLL_MS 100
/* Five seconds of headroom covers the 100 ms poll plus bounded send work before the v1 30 s deadline. */
#define HEARTBEAT_SEND_INTERVAL_US (25LL * 1000LL * 1000LL)
#define WIFI_RECONNECT_INITIAL_SECONDS 1
#define WIFI_RECONNECT_MAX_SECONDS 60
#define WEBSOCKET_TEXT_OPCODE 0x1
#define REMINDER_QUEUE_LENGTH 20
#define VOICE_TURN_EVENT_QUEUE_LENGTH 16

typedef struct {
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
    char reminder_id[DEVICE_PROTOCOL_REMINDER_ID_MAX_LEN];
} reminder_command_t;

typedef struct {
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
    wake_model_ota_request_t request;
} wake_model_command_t;

typedef struct {
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
    bool clear;
    expression_pack_request_t request;
} expression_pack_command_t;

typedef struct {
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
    firmware_ota_request_t request;
} firmware_command_t;

typedef struct {
    char turn_id[DEVICE_PROTOCOL_TURN_ID_LEN];
    device_voice_turn_stage_t stage;
    uint32_t elapsed_ms;
    device_voice_turn_failure_t failure;
} voice_turn_event_t;

typedef struct {
    esp_websocket_client_handle_t client;
    QueueHandle_t reminder_queue;
    QueueHandle_t wake_model_queue;
    QueueHandle_t expression_pack_queue;
    QueueHandle_t firmware_queue;
    SemaphoreHandle_t send_mutex;
    portMUX_TYPE sequence_lock;
    uint32_t next_sequence;
    volatile bool connected;
    volatile bool failed;
    bool heartbeat_sent;
    bool wake_model_report_sent;
    bool firmware_report_sent;
} websocket_connection_t;

static const char *TAG = "device_transport";
static EventGroupHandle_t s_transport_events;
static QueueHandle_t s_voice_turn_event_queue;
static esp_netif_t *s_wifi_sta_netif;
static bool s_netif_initialized_by_transport;
static bool s_event_loop_created_by_transport;
static bool s_wifi_sta_created_by_transport;
static bool s_wifi_initialized_by_transport;
static bool s_wifi_started_by_transport;
static bool s_ip_handler_registered;
static bool s_disconnect_handler_registered;
static bool s_wifi_credentials_configured;
static bool s_wifi_reconnect_pending;
static int64_t s_wifi_reconnect_due_us;
static uint32_t s_wifi_reconnect_delay_seconds = WIFI_RECONNECT_INITIAL_SECONDS;
static portMUX_TYPE s_wifi_retry_lock = portMUX_INITIALIZER_UNLOCKED;
static portMUX_TYPE s_server_connection_lock = portMUX_INITIALIZER_UNLOCKED;
static bool s_server_connected;

static void reset_wifi_reconnect_state(void);
static void schedule_wifi_reconnect(bool immediate);

bool device_transport_report_voice_turn(device_voice_turn_stage_t stage,
                                        const char *turn_id,
                                        uint32_t elapsed_ms,
                                        device_voice_turn_failure_t failure)
{
    if (s_voice_turn_event_queue == NULL || turn_id == NULL || strlen(turn_id) != 36) {
        return false;
    }
    voice_turn_event_t event = {
        .stage = stage,
        .elapsed_ms = elapsed_ms,
        .failure = failure,
    };
    memcpy(event.turn_id, turn_id, sizeof(event.turn_id) - 1);
    return xQueueSend(s_voice_turn_event_queue, &event, 0) == pdTRUE;
}

uint32_t device_transport_next_retry_seconds(uint32_t current_seconds)
{
    if (current_seconds == 0) {
        return WIFI_RECONNECT_INITIAL_SECONDS;
    }
    return current_seconds >= WIFI_RECONNECT_MAX_SECONDS / 2 ? WIFI_RECONNECT_MAX_SECONDS : current_seconds * 2;
}

bool device_transport_is_wifi_connected(void)
{
    return s_transport_events != NULL &&
           (xEventGroupGetBits(s_transport_events) & WIFI_CONNECTED_BIT) != 0;
}

static void set_server_connected(bool connected)
{
    taskENTER_CRITICAL(&s_server_connection_lock);
    s_server_connected = connected;
    taskEXIT_CRITICAL(&s_server_connection_lock);
}

bool device_transport_is_server_connected(void)
{
    bool connected;
    taskENTER_CRITICAL(&s_server_connection_lock);
    connected = s_server_connected;
    taskEXIT_CRITICAL(&s_server_connection_lock);
    return connected;
}

esp_err_t device_transport_configure_wifi(const char *ssid, const char *password)
{
    size_t ssid_length = ssid == NULL ? 0 : strnlen(ssid, sizeof(((wifi_config_t *)0)->sta.ssid) + 1);
    size_t password_length = password == NULL ? 0 : strnlen(password, sizeof(((wifi_config_t *)0)->sta.password));
    if (ssid_length == 0 || ssid_length > sizeof(((wifi_config_t *)0)->sta.ssid) ||
        password_length >= sizeof(((wifi_config_t *)0)->sta.password)) {
        return ESP_ERR_INVALID_ARG;
    }
    if (s_transport_events == NULL || !s_wifi_started_by_transport) {
        return ESP_ERR_INVALID_STATE;
    }

    wifi_config_t config = {0};
    memcpy(config.sta.ssid, ssid, ssid_length);
    memcpy(config.sta.password, password, password_length);
    esp_err_t err = esp_wifi_disconnect();
    if (err != ESP_OK && err != ESP_ERR_WIFI_NOT_CONNECT) {
        return err;
    }
    err = esp_wifi_set_config(WIFI_IF_STA, &config);
    if (err != ESP_OK) {
        return err;
    }

    s_wifi_credentials_configured = true;
    xEventGroupClearBits(s_transport_events, WIFI_CONNECTED_BIT);
    reset_wifi_reconnect_state();
    schedule_wifi_reconnect(true);
    return ESP_OK;
}

static uint32_t connection_next_sequence(websocket_connection_t *connection)
{
    uint32_t sequence = 0;
    taskENTER_CRITICAL(&connection->sequence_lock);
    if (connection->next_sequence != 0) {
        sequence = connection->next_sequence++;
    }
    taskEXIT_CRITICAL(&connection->sequence_lock);
    return sequence;
}

static bool connection_send_text(websocket_connection_t *connection, const char *payload)
{
    if (connection == NULL || connection->client == NULL || connection->send_mutex == NULL || payload == NULL ||
        xSemaphoreTake(connection->send_mutex, pdMS_TO_TICKS(1000)) != pdTRUE) {
        return false;
    }
    int sent = esp_websocket_client_send_text(connection->client, payload, strlen(payload), pdMS_TO_TICKS(1000));
    xSemaphoreGive(connection->send_mutex);
    return sent >= 0;
}

static int transport_rssi(void)
{
    wifi_ap_record_t access_point = {0};
    return esp_wifi_sta_get_ap_info(&access_point) == ESP_OK ? access_point.rssi : 0;
}

static void reset_wifi_reconnect_state(void)
{
    taskENTER_CRITICAL(&s_wifi_retry_lock);
    s_wifi_reconnect_pending = false;
    s_wifi_reconnect_due_us = 0;
    s_wifi_reconnect_delay_seconds = WIFI_RECONNECT_INITIAL_SECONDS;
    taskEXIT_CRITICAL(&s_wifi_retry_lock);
}

static void schedule_wifi_reconnect(bool immediate)
{
    if (!s_wifi_credentials_configured) {
        return;
    }

    uint32_t delay_seconds = 0;
    bool scheduled = false;
    taskENTER_CRITICAL(&s_wifi_retry_lock);
    if (!s_wifi_reconnect_pending) {
        delay_seconds = immediate ? 0 : s_wifi_reconnect_delay_seconds;
        s_wifi_reconnect_due_us = esp_timer_get_time() + (int64_t)delay_seconds * 1000LL * 1000LL;
        s_wifi_reconnect_pending = true;
        if (!immediate) {
            s_wifi_reconnect_delay_seconds = device_transport_next_retry_seconds(s_wifi_reconnect_delay_seconds);
        }
        scheduled = true;
    }
    taskEXIT_CRITICAL(&s_wifi_retry_lock);

    if (scheduled) {
        ESP_LOGW(TAG, "Wi-Fi reconnect scheduled in %lu seconds", (unsigned long)delay_seconds);
    }
}

static void service_wifi_reconnect(void)
{
    if (!s_wifi_credentials_configured ||
        (xEventGroupGetBits(s_transport_events) & WIFI_CONNECTED_BIT) != 0) {
        return;
    }

    bool attempt_reconnect = false;
    taskENTER_CRITICAL(&s_wifi_retry_lock);
    if (s_wifi_reconnect_pending && esp_timer_get_time() >= s_wifi_reconnect_due_us) {
        s_wifi_reconnect_pending = false;
        attempt_reconnect = true;
    }
    taskEXIT_CRITICAL(&s_wifi_retry_lock);

    if (!attempt_reconnect) {
        return;
    }

    esp_err_t err = esp_wifi_connect();
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Wi-Fi reconnect attempt failed: %s", esp_err_to_name(err));
        schedule_wifi_reconnect(false);
    }
}

static void wait_for_websocket_retry(uint32_t retry_seconds)
{
    int64_t deadline_us = esp_timer_get_time() + (int64_t)retry_seconds * 1000LL * 1000LL;
    while ((xEventGroupGetBits(s_transport_events) & WIFI_CONNECTED_BIT) != 0 &&
           esp_timer_get_time() < deadline_us) {
        int64_t remaining_us = deadline_us - esp_timer_get_time();
        uint32_t remaining_ms = (uint32_t)((remaining_us + 999) / 1000);
        uint32_t delay_ms = remaining_ms < TRANSPORT_IDLE_POLL_MS ? remaining_ms : TRANSPORT_IDLE_POLL_MS;
        TickType_t delay_ticks = pdMS_TO_TICKS(delay_ms);
        vTaskDelay(delay_ticks == 0 ? 1 : delay_ticks);
        service_wifi_reconnect();
    }
}

bool device_transport_build_authorization_header(const device_identity_t *identity, char *header, size_t size)
{
    if (header == NULL || size == 0) {
        return false;
    }
    header[0] = '\0';
    if (!device_identity_is_valid(identity)) {
        return false;
    }
    int written = snprintf(header, size, "Authorization: Bearer %s\r\n", identity->access_token);
    return written > 0 && (size_t)written < size;
}

static void send_command_ack(websocket_connection_t *connection,
                             const char *command_id,
                             bool accepted,
                             device_command_result_t result)
{
    char acknowledgement[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    uint32_t sequence = connection_next_sequence(connection);
    if (sequence == 0 ||
        device_protocol_encode_command_ack_with_result(
            acknowledgement, sizeof(acknowledgement), sequence, command_id, accepted, result) != ESP_OK ||
        !connection_send_text(connection, acknowledgement)) {
        connection->failed = true;
        safety_state_stop_motion();
    }
}

static void websocket_event_handler(void *handler_args,
                                    esp_event_base_t event_base,
                                    int32_t event_id,
                                    void *event_data)
{
    (void)event_base;
    websocket_connection_t *connection = handler_args;
    if (event_id == WEBSOCKET_EVENT_CONNECTED) {
        connection->connected = true;
        set_server_connected(true);
        companion_hardware_set_connected(true);
        ESP_LOGI(TAG, "Device WebSocket connected");
        return;
    }
    if (event_id == WEBSOCKET_EVENT_DISCONNECTED || event_id == WEBSOCKET_EVENT_ERROR) {
        connection->connected = false;
        connection->failed = true;
        set_server_connected(false);
        safety_state_stop_motion();
        companion_hardware_set_connected(false);
        ESP_LOGW(TAG, "Device WebSocket unavailable: event=%ld", (long)event_id);
        return;
    }
    if (event_id != WEBSOCKET_EVENT_DATA || event_data == NULL) {
        return;
    }

    esp_websocket_event_data_t *event = event_data;
    if (event->op_code != WEBSOCKET_TEXT_OPCODE || event->payload_offset != 0 ||
        event->data_len <= 0 || event->data_len != event->payload_len) {
        return;
    }

    device_command_t command = {0};
    if (!device_protocol_parse_command(event->data_ptr, (size_t)event->data_len, &command)) {
        return;
    }
    if (command.type == DEVICE_COMMAND_STOP_MOTION) {
        safety_state_stop_motion();
        send_command_ack(connection, command.command_id, true, DEVICE_COMMAND_RESULT_NONE);
        return;
    }
    if (command.type == DEVICE_COMMAND_STOP_AUDIO) {
        voice_control_cancel_active_turn();
        send_command_ack(connection, command.command_id, true, DEVICE_COMMAND_RESULT_NONE);
        return;
    }
    if (command.type == DEVICE_COMMAND_SPEAK_REMINDER) {
        companion_hardware_mark_activity();
        reminder_command_t reminder = {0};
        memcpy(reminder.command_id, command.command_id, sizeof(reminder.command_id));
        memcpy(reminder.reminder_id, command.reminder_id, sizeof(reminder.reminder_id));
        if (connection->reminder_queue == NULL || xQueueSend(connection->reminder_queue, &reminder, 0) != pdTRUE) {
            send_command_ack(connection, command.command_id, false, DEVICE_COMMAND_RESULT_FAILED);
        }
        return;
    }
    if (command.type == DEVICE_COMMAND_CONFIGURE_VOICE_DETECTION) {
        voice_wake_sensitivity_t sensitivity =
            command.wake_sensitivity == DEVICE_WAKE_SENSITIVITY_SENSITIVE
                ? VOICE_WAKE_SENSITIVITY_SENSITIVE
                : VOICE_WAKE_SENSITIVITY_NORMAL;
        bool accepted = voice_control_configure(
                            sensitivity,
                            (uint32_t)command.speech_start_threshold,
                            (uint32_t)command.speech_silence_threshold) == ESP_OK;
        send_command_ack(connection,
                         command.command_id,
                         accepted,
                         accepted ? DEVICE_COMMAND_RESULT_NONE : DEVICE_COMMAND_RESULT_FAILED);
        return;
    }
    if (command.type == DEVICE_COMMAND_CONFIGURE_INTERACTION) {
        bool accepted = companion_hardware_configure_interaction(
                            command.volume_percent, command.night_mode) == ESP_OK &&
                        voice_control_configure_continuous_conversation(
                            command.continuous_conversation_enabled,
                            (uint32_t)command.follow_up_window_seconds) == ESP_OK;
        send_command_ack(connection,
                         command.command_id,
                         accepted,
                         accepted ? DEVICE_COMMAND_RESULT_NONE : DEVICE_COMMAND_RESULT_FAILED);
        return;
    }
    if (command.type == DEVICE_COMMAND_INSTALL_WAKE_MODEL) {
        wake_model_command_t install = {0};
        memcpy(install.command_id, command.command_id, sizeof(install.command_id));
        memcpy(install.request.job_id, command.wake_model_job_id, sizeof(install.request.job_id));
        memcpy(install.request.model_name, command.wake_model_name, sizeof(install.request.model_name));
        memcpy(install.request.sha256, command.wake_model_sha256, sizeof(install.request.sha256));
        install.request.artifact_size = (size_t)command.wake_model_artifact_size;
        if (connection->wake_model_queue == NULL ||
            xQueueSend(connection->wake_model_queue, &install, 0) != pdTRUE) {
            send_command_ack(connection, command.command_id, false, DEVICE_COMMAND_RESULT_FAILED);
        }
        return;
    }
    if (command.type == DEVICE_COMMAND_INSTALL_EXPRESSION_PACK) {
        expression_pack_command_t install = {0};
        memcpy(install.command_id, command.command_id, sizeof(install.command_id));
        memcpy(install.request.pack_id, command.expression_pack_id, sizeof(install.request.pack_id));
        memcpy(install.request.sha256, command.expression_pack_sha256, sizeof(install.request.sha256));
        install.request.artifact_size = (size_t)command.expression_pack_artifact_size;
        if (connection->expression_pack_queue == NULL ||
            xQueueSend(connection->expression_pack_queue, &install, 0) != pdTRUE) {
            send_command_ack(connection, command.command_id, false, DEVICE_COMMAND_RESULT_FAILED);
        }
        return;
    }
    if (command.type == DEVICE_COMMAND_CLEAR_EXPRESSION_PACK) {
        expression_pack_command_t clear = {
            .clear = true,
        };
        memcpy(clear.command_id, command.command_id, sizeof(clear.command_id));
        if (connection->expression_pack_queue == NULL ||
            xQueueSend(connection->expression_pack_queue, &clear, 0) != pdTRUE) {
            send_command_ack(connection, command.command_id, false, DEVICE_COMMAND_RESULT_FAILED);
        }
        return;
    }
    if (command.type == DEVICE_COMMAND_INSTALL_FIRMWARE) {
        firmware_command_t install = {0};
        memcpy(install.command_id, command.command_id, sizeof(install.command_id));
        memcpy(install.request.job_id, command.firmware_job_id, sizeof(install.request.job_id));
        memcpy(install.request.version, command.firmware_version, sizeof(install.request.version));
        memcpy(install.request.sha256, command.firmware_sha256, sizeof(install.request.sha256));
        install.request.artifact_size = (size_t)command.firmware_artifact_size;
        if (connection->firmware_queue == NULL ||
            xQueueSend(connection->firmware_queue, &install, 0) != pdTRUE) {
            send_command_ack(connection, command.command_id, false, DEVICE_COMMAND_RESULT_FAILED);
        }
    }
}

static void wifi_event_handler(void *handler_args,
                               esp_event_base_t event_base,
                               int32_t event_id,
                               void *event_data)
{
    (void)handler_args;
    (void)event_data;
    if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        xEventGroupSetBits(s_transport_events, WIFI_CONNECTED_BIT);
        reset_wifi_reconnect_state();
        ESP_LOGI(TAG, "Wi-Fi connected; transport may connect when identity is available");
        return;
    }
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        xEventGroupClearBits(s_transport_events, WIFI_CONNECTED_BIT);
        set_server_connected(false);
        safety_state_stop_motion();
        companion_hardware_set_connected(false);
        schedule_wifi_reconnect(false);
        ESP_LOGW(TAG, "Wi-Fi disconnected; motion remains disabled");
    }
}

static void cleanup_wifi_monitor(void)
{
    if (s_ip_handler_registered) {
        (void)esp_event_handler_unregister(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event_handler);
        s_ip_handler_registered = false;
    }
    if (s_disconnect_handler_registered) {
        (void)esp_event_handler_unregister(WIFI_EVENT, WIFI_EVENT_STA_DISCONNECTED, wifi_event_handler);
        s_disconnect_handler_registered = false;
    }
    if (s_wifi_started_by_transport) {
        (void)esp_wifi_stop();
        s_wifi_started_by_transport = false;
    }
    if (s_wifi_initialized_by_transport) {
        (void)esp_wifi_deinit();
        s_wifi_initialized_by_transport = false;
    }
    if (s_wifi_sta_created_by_transport) {
        (void)esp_wifi_clear_default_wifi_driver_and_handlers(s_wifi_sta_netif);
        esp_netif_destroy(s_wifi_sta_netif);
        s_wifi_sta_created_by_transport = false;
        s_wifi_sta_netif = NULL;
    }
    if (s_event_loop_created_by_transport) {
        (void)esp_event_loop_delete_default();
        s_event_loop_created_by_transport = false;
    }
    if (s_netif_initialized_by_transport) {
        (void)esp_netif_deinit();
        s_netif_initialized_by_transport = false;
    }
    s_wifi_credentials_configured = false;
    reset_wifi_reconnect_state();
}

static esp_err_t initialize_wifi_monitor(void)
{
    esp_err_t err = esp_netif_init();
    if (err != ESP_OK) {
        return err;
    }
    s_netif_initialized_by_transport = true;
    err = esp_event_loop_create_default();
    if (err != ESP_OK) {
        return err;
    }
    s_event_loop_created_by_transport = true;

    wifi_init_config_t wifi_config = WIFI_INIT_CONFIG_DEFAULT();
    err = esp_wifi_init(&wifi_config);
    if (err != ESP_OK) {
        return err;
    }
    s_wifi_initialized_by_transport = true;

    esp_netif_config_t netif_config = ESP_NETIF_DEFAULT_WIFI_STA();
    s_wifi_sta_netif = esp_netif_new(&netif_config);
    if (s_wifi_sta_netif == NULL) {
        return ESP_ERR_NO_MEM;
    }
    s_wifi_sta_created_by_transport = true;
    err = esp_netif_attach_wifi_station(s_wifi_sta_netif);
    if (err != ESP_OK) {
        return err;
    }
    err = esp_wifi_set_default_wifi_sta_handlers();
    if (err != ESP_OK) {
        return err;
    }

    err = esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event_handler, NULL);
    if (err != ESP_OK) {
        return err;
    }
    s_ip_handler_registered = true;
    err = esp_event_handler_register(WIFI_EVENT, WIFI_EVENT_STA_DISCONNECTED, wifi_event_handler, NULL);
    if (err != ESP_OK) {
        return err;
    }
    s_disconnect_handler_registered = true;

    ESP_RETURN_ON_ERROR(esp_wifi_set_storage(WIFI_STORAGE_FLASH), TAG, "configure Wi-Fi storage");
    wifi_config_t saved_config = {0};
    err = esp_wifi_get_config(WIFI_IF_STA, &saved_config);
    if (err != ESP_OK) {
        return err;
    }
    s_wifi_credentials_configured = saved_config.sta.ssid[0] != '\0';
    if (!s_wifi_credentials_configured) {
        ESP_LOGW(TAG, "Wi-Fi credentials are unavailable; waiting without provisioning");
    }

    ESP_RETURN_ON_ERROR(esp_wifi_set_mode(WIFI_MODE_STA), TAG, "configure Wi-Fi station mode");
    err = esp_wifi_start();
    if (err != ESP_OK) {
        return err;
    }
    s_wifi_started_by_transport = true;
    if (s_wifi_credentials_configured) {
        schedule_wifi_reconnect(true);
    }
    return ESP_OK;
}

static bool run_websocket_connection(const device_identity_t *identity)
{
    char uri[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 32] = {0};
    char authorization_header[DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 32] = {0};
    if (!device_endpoint_build_websocket_uri(identity, uri, sizeof(uri)) ||
        !device_transport_build_authorization_header(identity, authorization_header, sizeof(authorization_header))) {
        ESP_LOGE(TAG, "Device WebSocket configuration is invalid");
        memset(authorization_header, 0, sizeof(authorization_header));
        memset(uri, 0, sizeof(uri));
        return false;
    }

    esp_websocket_client_config_t config = {
        .uri = uri,
        .headers = authorization_header,
        .buffer_size = DEVICE_PROTOCOL_MAX_MESSAGE_LEN,
        .disable_auto_reconnect = true,
        .task_name = "device_ws",
        .task_stack = WEBSOCKET_TASK_STACK_SIZE,
        .network_timeout_ms = WEBSOCKET_NETWORK_TIMEOUT_MS,
    };
    device_endpoint_configure_websocket_client(&config);
    websocket_connection_t connection = {
        .sequence_lock = portMUX_INITIALIZER_UNLOCKED,
        .next_sequence = 1,
    };
    connection.reminder_queue = xQueueCreate(REMINDER_QUEUE_LENGTH, sizeof(reminder_command_t));
    connection.wake_model_queue = xQueueCreate(1, sizeof(wake_model_command_t));
    connection.expression_pack_queue = xQueueCreate(1, sizeof(expression_pack_command_t));
    connection.firmware_queue = xQueueCreate(1, sizeof(firmware_command_t));
    connection.send_mutex = xSemaphoreCreateMutex();
    if (connection.reminder_queue == NULL || connection.wake_model_queue == NULL ||
        connection.expression_pack_queue == NULL || connection.firmware_queue == NULL ||
        connection.send_mutex == NULL) {
        if (connection.reminder_queue != NULL) {
            vQueueDelete(connection.reminder_queue);
        }
        if (connection.wake_model_queue != NULL) {
            vQueueDelete(connection.wake_model_queue);
        }
        if (connection.expression_pack_queue != NULL) {
            vQueueDelete(connection.expression_pack_queue);
        }
        if (connection.firmware_queue != NULL) {
            vQueueDelete(connection.firmware_queue);
        }
        if (connection.send_mutex != NULL) {
            vSemaphoreDelete(connection.send_mutex);
        }
        memset(authorization_header, 0, sizeof(authorization_header));
        memset(uri, 0, sizeof(uri));
        return false;
    }
    connection.client = esp_websocket_client_init(&config);
    if (connection.client == NULL) {
        ESP_LOGE(TAG, "WebSocket client allocation failed");
        vQueueDelete(connection.reminder_queue);
        vQueueDelete(connection.wake_model_queue);
        vQueueDelete(connection.expression_pack_queue);
        vQueueDelete(connection.firmware_queue);
        vSemaphoreDelete(connection.send_mutex);
        memset(authorization_header, 0, sizeof(authorization_header));
        memset(uri, 0, sizeof(uri));
        return false;
    }

    esp_err_t err = esp_websocket_register_events(connection.client, WEBSOCKET_EVENT_ANY, websocket_event_handler,
                                                   &connection);
    if (err == ESP_OK) {
        err = esp_websocket_client_start(connection.client);
    }
    int64_t next_heartbeat_us = esp_timer_get_time();
    while (err == ESP_OK && !connection.failed &&
           (xEventGroupGetBits(s_transport_events) & WIFI_CONNECTED_BIT) != 0) {
        int64_t now_us = esp_timer_get_time();
        if (connection.connected && now_us >= next_heartbeat_us) {
            char heartbeat[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
            uint32_t sequence = connection_next_sequence(&connection);
            const esp_app_desc_t *app_description = esp_app_get_description();
            if (sequence == 0 ||
                app_description == NULL ||
                device_protocol_encode_heartbeat_with_ota(
                    heartbeat, sizeof(heartbeat), sequence, 0, transport_rssi(),
                    app_description->version) != ESP_OK ||
                !connection_send_text(&connection, heartbeat)) {
                connection.failed = true;
                safety_state_stop_motion();
                break;
            }
            connection.heartbeat_sent = true;
            next_heartbeat_us = now_us + HEARTBEAT_SEND_INTERVAL_US;
        }
        if (connection.connected && !connection.wake_model_report_sent) {
            wake_model_ota_report_t report = {0};
            if (wake_model_ota_get_report(&report)) {
                char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
                uint32_t sequence = connection_next_sequence(&connection);
                const char *status = report.status == WAKE_MODEL_OTA_REPORT_INSTALLED
                                         ? "INSTALLED"
                                         : "ROLLED_BACK";
                if (sequence == 0 ||
                    device_protocol_encode_wake_model_status(
                        payload, sizeof(payload), sequence, report.job_id, status,
                        report.model_name, report.sha256) != ESP_OK ||
                    !connection_send_text(&connection, payload)) {
                    connection.failed = true;
                    safety_state_stop_motion();
                    break;
                }
                connection.wake_model_report_sent = true;
            }
        }
        if (connection.connected && !connection.firmware_report_sent) {
            firmware_ota_report_t report = {0};
            if (firmware_ota_get_report(&report)) {
                char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
                uint32_t sequence = connection_next_sequence(&connection);
                const char *status = report.status == FIRMWARE_OTA_REPORT_INSTALLED
                                         ? "INSTALLED" : "ROLLED_BACK";
                if (sequence == 0 ||
                    device_protocol_encode_firmware_update_status(
                        payload, sizeof(payload), sequence, report.job_id, status,
                        report.version, report.sha256) != ESP_OK ||
                    !connection_send_text(&connection, payload)) {
                    connection.failed = true;
                    safety_state_stop_motion();
                    break;
                }
                connection.firmware_report_sent = true;
            }
        }
        firmware_command_t firmware_install = {0};
        if (connection.connected &&
            xQueueReceive(connection.firmware_queue, &firmware_install, 0) == pdTRUE) {
            bool accepted = firmware_ota_install(identity, &firmware_install.request) == ESP_OK;
            send_command_ack(&connection, firmware_install.command_id, accepted,
                             accepted ? DEVICE_COMMAND_RESULT_NONE : DEVICE_COMMAND_RESULT_FAILED);
            if (accepted) {
                ESP_LOGI(TAG, "Firmware verified; restarting into pending OTA partition");
                vTaskDelay(pdMS_TO_TICKS(250));
                esp_restart();
            }
        }
        wake_model_command_t install = {0};
        if (connection.connected &&
            xQueueReceive(connection.wake_model_queue, &install, 0) == pdTRUE) {
            bool accepted = wake_model_ota_install(identity, &install.request) == ESP_OK;
            send_command_ack(&connection,
                             install.command_id,
                             accepted,
                             accepted ? DEVICE_COMMAND_RESULT_NONE : DEVICE_COMMAND_RESULT_FAILED);
            if (accepted) {
                ESP_LOGI(TAG, "Wake model verified; restarting into pending slot");
                vTaskDelay(pdMS_TO_TICKS(250));
                esp_restart();
            }
        }
        expression_pack_command_t expression_install = {0};
        if (connection.connected &&
            xQueueReceive(connection.expression_pack_queue, &expression_install, 0) == pdTRUE) {
            bool accepted = expression_install.clear
                                ? expression_pack_clear() == ESP_OK
                                : expression_pack_install(identity, &expression_install.request) == ESP_OK;
            if (accepted) {
                companion_hardware_refresh_face();
            }
            send_command_ack(&connection,
                             expression_install.command_id,
                             accepted,
                             accepted ? DEVICE_COMMAND_RESULT_NONE : DEVICE_COMMAND_RESULT_FAILED);
        }
        reminder_command_t reminder = {0};
        if (connection.connected &&
            xQueueReceive(connection.reminder_queue, &reminder, 0) == pdTRUE) {
            bool cancelled = false;
            esp_err_t reminder_err = voice_control_play_reminder(
                identity, reminder.reminder_id, &cancelled);
            bool accepted = reminder_err == ESP_OK && !cancelled;
            send_command_ack(
                &connection,
                reminder.command_id,
                accepted,
                accepted ? DEVICE_COMMAND_RESULT_NONE
                         : (cancelled ? DEVICE_COMMAND_RESULT_CANCELLED : DEVICE_COMMAND_RESULT_FAILED));
        }
        voice_turn_event_t voice_turn_event = {0};
        if (connection.connected &&
            xQueueReceive(s_voice_turn_event_queue, &voice_turn_event, 0) == pdTRUE) {
            char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
            uint32_t sequence = connection_next_sequence(&connection);
            if (sequence == 0 ||
                device_protocol_encode_voice_turn_stage(
                    payload,
                    sizeof(payload),
                    sequence,
                    voice_turn_event.turn_id,
                    voice_turn_event.stage,
                    voice_turn_event.elapsed_ms,
                    voice_turn_event.failure) != ESP_OK ||
                !connection_send_text(&connection, payload)) {
                ESP_LOGW(TAG, "Voice turn diagnostic event was dropped safely");
            }
        }
        vTaskDelay(pdMS_TO_TICKS(100));
    }

    safety_state_stop_motion();
    set_server_connected(false);
    companion_hardware_set_connected(false);
    (void)esp_websocket_client_stop(connection.client);
    (void)esp_websocket_client_destroy(connection.client);
    vQueueDelete(connection.reminder_queue);
    vQueueDelete(connection.wake_model_queue);
    vQueueDelete(connection.expression_pack_queue);
    vQueueDelete(connection.firmware_queue);
    vSemaphoreDelete(connection.send_mutex);
    memset(authorization_header, 0, sizeof(authorization_header));
    memset(uri, 0, sizeof(uri));
    return connection.heartbeat_sent;
}

static void transport_task(void *argument)
{
    (void)argument;
    uint32_t retry_seconds = 1;
    bool logged_waiting_for_identity = false;
    bool logged_waiting_for_wifi = false;
    for (;;) {
        service_wifi_reconnect();
        device_identity_t identity = {0};
        esp_err_t identity_err = device_identity_load(&identity);
        if (identity_err != ESP_OK) {
            if (!logged_waiting_for_identity) {
                if (identity_err == ESP_ERR_INVALID_ARG) {
                    ESP_LOGW(TAG, "needs_usb_repair");
                } else {
                    ESP_LOGW(TAG, "Valid device identity unavailable; waiting before transport start");
                }
                logged_waiting_for_identity = true;
            }
            logged_waiting_for_wifi = false;
            safety_state_stop_motion();
            vTaskDelay(pdMS_TO_TICKS(TRANSPORT_IDLE_POLL_MS));
            continue;
        }
        logged_waiting_for_identity = false;
        if ((xEventGroupGetBits(s_transport_events) & WIFI_CONNECTED_BIT) == 0) {
            if (!logged_waiting_for_wifi) {
                ESP_LOGW(TAG, "Waiting for Wi-Fi connection before transport start");
                logged_waiting_for_wifi = true;
            }
            safety_state_stop_motion();
            vTaskDelay(pdMS_TO_TICKS(TRANSPORT_IDLE_POLL_MS));
            continue;
        }
        logged_waiting_for_wifi = false;

        device_credential_refresh_result_t refresh = device_credentials_refresh(&identity);
        if (refresh == DEVICE_CREDENTIAL_REFRESHED) {
            if (device_identity_save(&identity) != ESP_OK) {
                safety_state_stop_motion();
                continue;
            }
        } else if (refresh == DEVICE_CREDENTIAL_REPAIR_REQUIRED) {
            ESP_LOGW(TAG, "needs_usb_repair");
            safety_state_stop_motion();
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }

        bool healthy_connection = run_websocket_connection(&identity);
        safety_state_stop_motion();
        if ((xEventGroupGetBits(s_transport_events) & WIFI_CONNECTED_BIT) != 0) {
            ESP_LOGW(TAG, "WebSocket stopped; reconnecting in %lu seconds", (unsigned long)retry_seconds);
            wait_for_websocket_retry(retry_seconds);
            retry_seconds = (xEventGroupGetBits(s_transport_events) & WIFI_CONNECTED_BIT) != 0
                                ? healthy_connection ? WIFI_RECONNECT_INITIAL_SECONDS
                                                     : device_transport_next_retry_seconds(retry_seconds)
                                : WIFI_RECONNECT_INITIAL_SECONDS;
        } else {
            retry_seconds = WIFI_RECONNECT_INITIAL_SECONDS;
        }
    }
}

esp_err_t device_transport_start(void)
{
    if (s_transport_events != NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    s_transport_events = xEventGroupCreate();
    s_voice_turn_event_queue = xQueueCreate(VOICE_TURN_EVENT_QUEUE_LENGTH, sizeof(voice_turn_event_t));
    if (s_transport_events == NULL || s_voice_turn_event_queue == NULL) {
        if (s_transport_events != NULL) {
            vEventGroupDelete(s_transport_events);
            s_transport_events = NULL;
        }
        if (s_voice_turn_event_queue != NULL) {
            vQueueDelete(s_voice_turn_event_queue);
            s_voice_turn_event_queue = NULL;
        }
        return ESP_ERR_NO_MEM;
    }

    esp_err_t wifi_err = initialize_wifi_monitor();
    if (wifi_err != ESP_OK) {
        cleanup_wifi_monitor();
        vEventGroupDelete(s_transport_events);
        s_transport_events = NULL;
        vQueueDelete(s_voice_turn_event_queue);
        s_voice_turn_event_queue = NULL;
        return wifi_err;
    }
    BaseType_t created = xTaskCreate(transport_task, "device_transport", TRANSPORT_TASK_STACK_SIZE, NULL,
                                     TRANSPORT_TASK_PRIORITY, NULL);
    if (created == pdPASS) {
        return ESP_OK;
    }
    cleanup_wifi_monitor();
    vEventGroupDelete(s_transport_events);
    s_transport_events = NULL;
    vQueueDelete(s_voice_turn_event_queue);
    s_voice_turn_event_queue = NULL;
    return ESP_ERR_NO_MEM;
}
