#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#define DEVICE_PROTOCOL_MAX_MESSAGE_LEN 384
#define DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN 96
#define DEVICE_PROTOCOL_REMINDER_ID_MAX_LEN 37
#define DEVICE_PROTOCOL_SPEECH_START_THRESHOLD_MIN 100
#define DEVICE_PROTOCOL_SPEECH_START_THRESHOLD_MAX 5000
#define DEVICE_PROTOCOL_SPEECH_SILENCE_THRESHOLD_MIN 50
#define DEVICE_PROTOCOL_SPEECH_SILENCE_THRESHOLD_MAX 4000

typedef enum {
    DEVICE_WAKE_SENSITIVITY_NORMAL = 0,
    DEVICE_WAKE_SENSITIVITY_SENSITIVE,
} device_wake_sensitivity_t;

typedef enum {
    DEVICE_COMMAND_NONE = 0,
    DEVICE_COMMAND_STOP_MOTION,
    DEVICE_COMMAND_SPEAK_REMINDER,
    DEVICE_COMMAND_CONFIGURE_VOICE_DETECTION,
} device_command_type_t;

typedef struct {
    device_command_type_t type;
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
    char reminder_id[DEVICE_PROTOCOL_REMINDER_ID_MAX_LEN];
    device_wake_sensitivity_t wake_sensitivity;
    int speech_start_threshold;
    int speech_silence_threshold;
} device_command_t;

esp_err_t device_protocol_encode_heartbeat(char *output,
                                           size_t output_size,
                                           uint32_t sequence,
                                           int battery_percent,
                                           int rssi,
                                           const char *firmware_version);

bool device_protocol_parse_stop_motion(const char *payload,
                                       size_t payload_size,
                                       char *command_id,
                                       size_t command_id_size);

bool device_protocol_parse_command(const char *payload,
                                   size_t payload_size,
                                   device_command_t *command);

esp_err_t device_protocol_encode_command_ack(char *output,
                                             size_t output_size,
                                             uint32_t sequence,
                                             const char *command_id,
                                             bool accepted);
