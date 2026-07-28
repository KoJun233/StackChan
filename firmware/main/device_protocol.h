#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#define DEVICE_PROTOCOL_MAX_MESSAGE_LEN 512
#define DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN 96
#define DEVICE_PROTOCOL_REMINDER_ID_MAX_LEN 37
#define DEVICE_PROTOCOL_WAKE_MODEL_JOB_ID_MAX_LEN 37
#define DEVICE_PROTOCOL_WAKE_MODEL_NAME_MAX_LEN 32
#define DEVICE_PROTOCOL_SHA256_MAX_LEN 65
#define DEVICE_PROTOCOL_WAKE_MODEL_MAX_SIZE (1024 * 1024)
#define DEVICE_PROTOCOL_EXPRESSION_PACK_ID_MAX_LEN 37
#define DEVICE_PROTOCOL_EXPRESSION_PACK_MAX_SIZE (1536 * 1024)
#define DEVICE_PROTOCOL_TURN_ID_LEN 37
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
    DEVICE_COMMAND_STOP_AUDIO,
    DEVICE_COMMAND_SPEAK_REMINDER,
    DEVICE_COMMAND_CONFIGURE_VOICE_DETECTION,
    DEVICE_COMMAND_CONFIGURE_INTERACTION,
    DEVICE_COMMAND_INSTALL_WAKE_MODEL,
    DEVICE_COMMAND_INSTALL_EXPRESSION_PACK,
    DEVICE_COMMAND_CLEAR_EXPRESSION_PACK,
} device_command_type_t;

typedef enum {
    DEVICE_VOICE_STAGE_WAKE_DETECTED = 0,
    DEVICE_VOICE_STAGE_TOUCH_STARTED,
    DEVICE_VOICE_STAGE_LISTENING,
    DEVICE_VOICE_STAGE_SPEECH_CAPTURED,
    DEVICE_VOICE_STAGE_UPLOAD_STARTED,
    DEVICE_VOICE_STAGE_PLAYBACK_STARTED,
    DEVICE_VOICE_STAGE_PLAYBACK_COMPLETED,
    DEVICE_VOICE_STAGE_LISTENING_RESUMED,
    DEVICE_VOICE_STAGE_CANCELLED,
    DEVICE_VOICE_STAGE_FAILED,
} device_voice_turn_stage_t;

typedef enum {
    DEVICE_COMMAND_RESULT_NONE = 0,
    DEVICE_COMMAND_RESULT_CANCELLED,
    DEVICE_COMMAND_RESULT_FAILED,
} device_command_result_t;

typedef enum {
    DEVICE_VOICE_FAILURE_NONE = 0,
    DEVICE_VOICE_FAILURE_NO_SPEECH,
    DEVICE_VOICE_FAILURE_OFFLINE,
    DEVICE_VOICE_FAILURE_OUT_OF_MEMORY,
    DEVICE_VOICE_FAILURE_UPLOAD_FAILED,
    DEVICE_VOICE_FAILURE_INVALID_RESPONSE,
    DEVICE_VOICE_FAILURE_PLAYBACK_FAILED,
    DEVICE_VOICE_FAILURE_MICROPHONE_RECOVERY_FAILED,
    DEVICE_VOICE_FAILURE_INTERNAL_ERROR,
} device_voice_turn_failure_t;

typedef struct {
    device_command_type_t type;
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN];
    char reminder_id[DEVICE_PROTOCOL_REMINDER_ID_MAX_LEN];
    device_wake_sensitivity_t wake_sensitivity;
    int speech_start_threshold;
    int speech_silence_threshold;
    int volume_percent;
    bool night_mode;
    char wake_model_job_id[DEVICE_PROTOCOL_WAKE_MODEL_JOB_ID_MAX_LEN];
    char wake_model_name[DEVICE_PROTOCOL_WAKE_MODEL_NAME_MAX_LEN];
    char wake_model_sha256[DEVICE_PROTOCOL_SHA256_MAX_LEN];
    int wake_model_artifact_size;
    char expression_pack_id[DEVICE_PROTOCOL_EXPRESSION_PACK_ID_MAX_LEN];
    char expression_pack_sha256[DEVICE_PROTOCOL_SHA256_MAX_LEN];
    int expression_pack_artifact_size;
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

esp_err_t device_protocol_encode_command_ack_with_result(char *output,
                                                         size_t output_size,
                                                         uint32_t sequence,
                                                         const char *command_id,
                                                         bool accepted,
                                                         device_command_result_t result);

esp_err_t device_protocol_encode_wake_model_status(char *output,
                                                   size_t output_size,
                                                   uint32_t sequence,
                                                   const char *job_id,
                                                   const char *status,
                                                   const char *model_name,
                                                   const char *sha256);

esp_err_t device_protocol_encode_voice_turn_stage(char *output,
                                                  size_t output_size,
                                                  uint32_t sequence,
                                                  const char *turn_id,
                                                  device_voice_turn_stage_t stage,
                                                  uint32_t elapsed_ms,
                                                  device_voice_turn_failure_t failure);
