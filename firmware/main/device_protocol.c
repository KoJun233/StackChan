#include "device_protocol.h"

#include <ctype.h>
#include <string.h>

#include "cJSON.h"

static bool is_nonblank(const char *value)
{
    if (value == NULL) {
        return false;
    }
    for (const char *cursor = value; *cursor != '\0'; ++cursor) {
        if (!isspace((unsigned char)*cursor)) {
            return true;
        }
    }
    return false;
}

static bool is_valid_firmware_version(const char *firmware_version)
{
    size_t length = firmware_version == NULL ? 0 : strnlen(firmware_version, 81);
    if (length == 0 || length > 80) {
        return false;
    }
    for (size_t index = 0; index < length; ++index) {
        unsigned char character = (unsigned char)firmware_version[index];
        if (!isalnum(character) && character != '.' && character != '_' && character != '-') {
            return false;
        }
    }
    return true;
}

static bool is_valid_command_id(const char *command_id)
{
    size_t command_id_size = command_id == NULL ? 0 : strnlen(command_id, DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN);
    return command_id_size > 0 && command_id_size < DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN &&
           is_nonblank(command_id);
}

static bool is_valid_uuid(const char *value)
{
    if (value == NULL || strlen(value) != 36) {
        return false;
    }
    for (size_t index = 0; index < 36; ++index) {
        unsigned char character = (unsigned char)value[index];
        if (index == 8 || index == 13 || index == 18 || index == 23) {
            if (character != '-') {
                return false;
            }
        } else if (!isxdigit(character)) {
            return false;
        }
    }
    return true;
}

static bool is_valid_wake_model_name(const char *value)
{
    size_t length = value == NULL ? 0 : strnlen(value, DEVICE_PROTOCOL_WAKE_MODEL_NAME_MAX_LEN);
    if (length == 0 || length >= DEVICE_PROTOCOL_WAKE_MODEL_NAME_MAX_LEN) {
        return false;
    }
    for (size_t index = 0; index < length; ++index) {
        unsigned char character = (unsigned char)value[index];
        if (!(islower(character) || isdigit(character) || character == '_')) {
            return false;
        }
    }
    return true;
}

static bool is_valid_sha256(const char *value)
{
    if (value == NULL || strlen(value) != 64) {
        return false;
    }
    for (size_t index = 0; index < 64; ++index) {
        unsigned char character = (unsigned char)value[index];
        if (!(isdigit(character) || (character >= 'a' && character <= 'f'))) {
            return false;
        }
    }
    return true;
}

static const char *voice_stage_name(device_voice_turn_stage_t stage)
{
    switch (stage) {
    case DEVICE_VOICE_STAGE_WAKE_DETECTED:
        return "WAKE_DETECTED";
    case DEVICE_VOICE_STAGE_TOUCH_STARTED:
        return "TOUCH_STARTED";
    case DEVICE_VOICE_STAGE_LISTENING:
        return "LISTENING";
    case DEVICE_VOICE_STAGE_FOLLOW_UP_LISTENING:
        return "FOLLOW_UP_LISTENING";
    case DEVICE_VOICE_STAGE_SPEECH_CAPTURED:
        return "SPEECH_CAPTURED";
    case DEVICE_VOICE_STAGE_UPLOAD_STARTED:
        return "UPLOAD_STARTED";
    case DEVICE_VOICE_STAGE_PLAYBACK_STARTED:
        return "PLAYBACK_STARTED";
    case DEVICE_VOICE_STAGE_PLAYBACK_COMPLETED:
        return "PLAYBACK_COMPLETED";
    case DEVICE_VOICE_STAGE_FOLLOW_UP_TIMEOUT:
        return "FOLLOW_UP_TIMEOUT";
    case DEVICE_VOICE_STAGE_CONVERSATION_ENDED:
        return "CONVERSATION_ENDED";
    case DEVICE_VOICE_STAGE_LISTENING_RESUMED:
        return "LISTENING_RESUMED";
    case DEVICE_VOICE_STAGE_CANCELLED:
        return "CANCELLED";
    case DEVICE_VOICE_STAGE_FAILED:
        return "FAILED";
    default:
        return NULL;
    }
}

static const char *command_result_name(device_command_result_t result)
{
    switch (result) {
    case DEVICE_COMMAND_RESULT_CANCELLED:
        return "cancelled";
    case DEVICE_COMMAND_RESULT_FAILED:
        return "failed";
    case DEVICE_COMMAND_RESULT_NONE:
    default:
        return NULL;
    }
}

static const char *voice_failure_name(device_voice_turn_failure_t failure)
{
    switch (failure) {
    case DEVICE_VOICE_FAILURE_NO_SPEECH:
        return "NO_SPEECH";
    case DEVICE_VOICE_FAILURE_OFFLINE:
        return "OFFLINE";
    case DEVICE_VOICE_FAILURE_OUT_OF_MEMORY:
        return "OUT_OF_MEMORY";
    case DEVICE_VOICE_FAILURE_UPLOAD_FAILED:
        return "UPLOAD_FAILED";
    case DEVICE_VOICE_FAILURE_INVALID_RESPONSE:
        return "INVALID_RESPONSE";
    case DEVICE_VOICE_FAILURE_PLAYBACK_FAILED:
        return "PLAYBACK_FAILED";
    case DEVICE_VOICE_FAILURE_MICROPHONE_RECOVERY_FAILED:
        return "MICROPHONE_RECOVERY_FAILED";
    case DEVICE_VOICE_FAILURE_INTERNAL_ERROR:
        return "INTERNAL_ERROR";
    default:
        return NULL;
    }
}

static bool is_integer_in_range(const cJSON *value, int minimum, int maximum)
{
    return cJSON_IsNumber(value) && value->valuedouble == (double)value->valueint &&
           value->valueint >= minimum && value->valueint <= maximum;
}

static esp_err_t print_json(cJSON *root, char *output, size_t output_size)
{
    if (root == NULL || output == NULL || output_size == 0) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(output, 0, output_size);
    return cJSON_PrintPreallocated(root, output, output_size, false) ? ESP_OK : ESP_ERR_NO_MEM;
}

esp_err_t device_protocol_encode_heartbeat(char *output,
                                           size_t output_size,
                                           uint32_t sequence,
                                           int battery_percent,
                                           int rssi,
                                           const char *firmware_version)
{
    if (output == NULL || output_size == 0 || sequence == 0 || battery_percent < 0 || battery_percent > 100 ||
        !is_valid_firmware_version(firmware_version)) {
        return ESP_ERR_INVALID_ARG;
    }

    cJSON *root = cJSON_CreateObject();
    if (root == NULL) {
        return ESP_ERR_NO_MEM;
    }
    bool complete = cJSON_AddStringToObject(root, "type", "heartbeat") != NULL &&
                    cJSON_AddNumberToObject(root, "sequence", sequence) != NULL &&
                    cJSON_AddNumberToObject(root, "battery_percent", battery_percent) != NULL &&
                    cJSON_AddNumberToObject(root, "rssi", rssi) != NULL &&
                    cJSON_AddStringToObject(root, "safety_state", "motion_disabled") != NULL &&
                    cJSON_AddStringToObject(root, "firmware_version", firmware_version) != NULL;
    esp_err_t err = complete ? print_json(root, output, output_size) : ESP_ERR_NO_MEM;
    cJSON_Delete(root);
    return err;
}

bool device_protocol_parse_stop_motion(const char *payload,
                                       size_t payload_size,
                                       char *command_id,
                                       size_t command_id_size)
{
    if (command_id == NULL || command_id_size < DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN) {
        return false;
    }
    command_id[0] = '\0';

    device_command_t command = {0};
    if (!device_protocol_parse_command(payload, payload_size, &command) ||
        command.type != DEVICE_COMMAND_STOP_MOTION) {
        return false;
    }
    memcpy(command_id, command.command_id, strlen(command.command_id) + 1);
    return true;
}

bool device_protocol_parse_command(const char *payload,
                                   size_t payload_size,
                                   device_command_t *command)
{
    if (command == NULL) {
        return false;
    }
    memset(command, 0, sizeof(*command));

    if (payload == NULL || payload_size == 0 || payload_size >= DEVICE_PROTOCOL_MAX_MESSAGE_LEN ||
        memchr(payload, '\0', payload_size) != NULL) {
        return false;
    }

    const char *parse_end = NULL;
    cJSON *root = cJSON_ParseWithLengthOpts(payload, payload_size, &parse_end, false);
    if (root == NULL || !cJSON_IsObject(root) || parse_end == NULL) {
        cJSON_Delete(root);
        return false;
    }

    while (parse_end < payload + payload_size) {
        if (!isspace((unsigned char)*parse_end)) {
            cJSON_Delete(root);
            return false;
        }
        ++parse_end;
    }

    cJSON *type = cJSON_GetObjectItemCaseSensitive(root, "type");
    cJSON *id = cJSON_GetObjectItemCaseSensitive(root, "command_id");
    bool valid = cJSON_IsString(type) && type->valuestring != NULL && cJSON_IsString(id) &&
                 id->valuestring != NULL && is_valid_command_id(id->valuestring);
    if (valid && strcmp(type->valuestring, "stop_motion") == 0 && cJSON_GetArraySize(root) == 2) {
        command->type = DEVICE_COMMAND_STOP_MOTION;
    } else if (valid && strcmp(type->valuestring, "stop_audio") == 0 && cJSON_GetArraySize(root) == 2) {
        command->type = DEVICE_COMMAND_STOP_AUDIO;
    } else if (valid && strcmp(type->valuestring, "speak_reminder") == 0 && cJSON_GetArraySize(root) == 3) {
        cJSON *reminder_id = cJSON_GetObjectItemCaseSensitive(root, "reminder_id");
        valid = cJSON_IsString(reminder_id) && reminder_id->valuestring != NULL &&
                is_valid_uuid(reminder_id->valuestring);
        if (valid) {
            command->type = DEVICE_COMMAND_SPEAK_REMINDER;
            memcpy(command->reminder_id, reminder_id->valuestring, DEVICE_PROTOCOL_REMINDER_ID_MAX_LEN);
        }
    } else if (valid && strcmp(type->valuestring, "configure_voice_detection") == 0 &&
               cJSON_GetArraySize(root) == 5) {
        cJSON *wake_sensitivity = cJSON_GetObjectItemCaseSensitive(root, "wake_sensitivity");
        cJSON *speech_start_threshold = cJSON_GetObjectItemCaseSensitive(root, "speech_start_threshold");
        cJSON *speech_silence_threshold = cJSON_GetObjectItemCaseSensitive(root, "speech_silence_threshold");
        valid = cJSON_IsString(wake_sensitivity) && wake_sensitivity->valuestring != NULL &&
                is_integer_in_range(speech_start_threshold,
                                    DEVICE_PROTOCOL_SPEECH_START_THRESHOLD_MIN,
                                    DEVICE_PROTOCOL_SPEECH_START_THRESHOLD_MAX) &&
                is_integer_in_range(speech_silence_threshold,
                                    DEVICE_PROTOCOL_SPEECH_SILENCE_THRESHOLD_MIN,
                                    DEVICE_PROTOCOL_SPEECH_SILENCE_THRESHOLD_MAX) &&
                speech_silence_threshold->valueint < speech_start_threshold->valueint;
        if (valid && strcmp(wake_sensitivity->valuestring, "NORMAL") == 0) {
            command->wake_sensitivity = DEVICE_WAKE_SENSITIVITY_NORMAL;
        } else if (valid && strcmp(wake_sensitivity->valuestring, "SENSITIVE") == 0) {
            command->wake_sensitivity = DEVICE_WAKE_SENSITIVITY_SENSITIVE;
        } else {
            valid = false;
        }
        if (valid) {
            command->type = DEVICE_COMMAND_CONFIGURE_VOICE_DETECTION;
            command->speech_start_threshold = speech_start_threshold->valueint;
            command->speech_silence_threshold = speech_silence_threshold->valueint;
        }
    } else if (valid && strcmp(type->valuestring, "configure_interaction") == 0 &&
               (cJSON_GetArraySize(root) == 4 || cJSON_GetArraySize(root) == 6)) {
        cJSON *volume_percent = cJSON_GetObjectItemCaseSensitive(root, "volume_percent");
        cJSON *night_mode = cJSON_GetObjectItemCaseSensitive(root, "night_mode");
        valid = is_integer_in_range(volume_percent, 0, 100) && cJSON_IsBool(night_mode);
        command->continuous_conversation_enabled = false;
        command->follow_up_window_seconds = DEVICE_PROTOCOL_FOLLOW_UP_WINDOW_SECONDS_DEFAULT;
        if (valid && cJSON_GetArraySize(root) == 6) {
            cJSON *continuous_enabled = cJSON_GetObjectItemCaseSensitive(
                root, "continuous_conversation_enabled");
            cJSON *follow_up_window_seconds = cJSON_GetObjectItemCaseSensitive(
                root, "follow_up_window_seconds");
            valid = cJSON_IsBool(continuous_enabled) && cJSON_IsTrue(continuous_enabled) &&
                    is_integer_in_range(follow_up_window_seconds,
                                        DEVICE_PROTOCOL_FOLLOW_UP_WINDOW_SECONDS_MIN,
                                        DEVICE_PROTOCOL_FOLLOW_UP_WINDOW_SECONDS_MAX);
            if (valid) {
                command->continuous_conversation_enabled = true;
                command->follow_up_window_seconds = follow_up_window_seconds->valueint;
            }
        }
        if (valid) {
            command->type = DEVICE_COMMAND_CONFIGURE_INTERACTION;
            command->volume_percent = volume_percent->valueint;
            command->night_mode = cJSON_IsTrue(night_mode);
        }
    } else if (valid && strcmp(type->valuestring, "install_wake_model") == 0 &&
               cJSON_GetArraySize(root) == 6) {
        cJSON *job_id = cJSON_GetObjectItemCaseSensitive(root, "job_id");
        cJSON *model_name = cJSON_GetObjectItemCaseSensitive(root, "model_name");
        cJSON *sha256 = cJSON_GetObjectItemCaseSensitive(root, "sha256");
        cJSON *artifact_size = cJSON_GetObjectItemCaseSensitive(root, "artifact_size");
        valid = cJSON_IsString(job_id) && job_id->valuestring != NULL &&
                is_valid_uuid(job_id->valuestring) &&
                cJSON_IsString(model_name) && model_name->valuestring != NULL &&
                is_valid_wake_model_name(model_name->valuestring) &&
                cJSON_IsString(sha256) && sha256->valuestring != NULL &&
                is_valid_sha256(sha256->valuestring) &&
                is_integer_in_range(artifact_size, 1, DEVICE_PROTOCOL_WAKE_MODEL_MAX_SIZE);
        if (valid) {
            command->type = DEVICE_COMMAND_INSTALL_WAKE_MODEL;
            memcpy(command->wake_model_job_id, job_id->valuestring,
                   strlen(job_id->valuestring) + 1);
            memcpy(command->wake_model_name, model_name->valuestring,
                   strlen(model_name->valuestring) + 1);
            memcpy(command->wake_model_sha256, sha256->valuestring,
                   strlen(sha256->valuestring) + 1);
            command->wake_model_artifact_size = artifact_size->valueint;
        }
    } else if (valid && strcmp(type->valuestring, "install_expression_pack") == 0 &&
               cJSON_GetArraySize(root) == 5) {
        cJSON *pack_id = cJSON_GetObjectItemCaseSensitive(root, "pack_id");
        cJSON *sha256 = cJSON_GetObjectItemCaseSensitive(root, "sha256");
        cJSON *artifact_size = cJSON_GetObjectItemCaseSensitive(root, "artifact_size");
        valid = cJSON_IsString(pack_id) && pack_id->valuestring != NULL &&
                is_valid_uuid(pack_id->valuestring) &&
                cJSON_IsString(sha256) && sha256->valuestring != NULL &&
                is_valid_sha256(sha256->valuestring) &&
                is_integer_in_range(artifact_size, 1, DEVICE_PROTOCOL_EXPRESSION_PACK_MAX_SIZE);
        if (valid) {
            command->type = DEVICE_COMMAND_INSTALL_EXPRESSION_PACK;
            memcpy(command->expression_pack_id, pack_id->valuestring,
                   strlen(pack_id->valuestring) + 1);
            memcpy(command->expression_pack_sha256, sha256->valuestring,
                   strlen(sha256->valuestring) + 1);
            command->expression_pack_artifact_size = artifact_size->valueint;
        }
    } else if (valid && strcmp(type->valuestring, "clear_expression_pack") == 0 &&
               cJSON_GetArraySize(root) == 2) {
        command->type = DEVICE_COMMAND_CLEAR_EXPRESSION_PACK;
    } else {
        valid = false;
    }
    if (valid) {
        memcpy(command->command_id, id->valuestring, strlen(id->valuestring) + 1);
    }
    cJSON_Delete(root);
    if (!valid) {
        memset(command, 0, sizeof(*command));
    }
    return valid;
}

esp_err_t device_protocol_encode_command_ack(char *output,
                                             size_t output_size,
                                             uint32_t sequence,
                                             const char *command_id,
                                             bool accepted)
{
    return device_protocol_encode_command_ack_with_result(
        output, output_size, sequence, command_id, accepted, DEVICE_COMMAND_RESULT_NONE);
}

esp_err_t device_protocol_encode_command_ack_with_result(char *output,
                                                         size_t output_size,
                                                         uint32_t sequence,
                                                         const char *command_id,
                                                         bool accepted,
                                                         device_command_result_t result)
{
    const char *result_name = command_result_name(result);
    if (output == NULL || output_size == 0 || sequence == 0 ||
        !is_valid_command_id(command_id) ||
        (accepted && result != DEVICE_COMMAND_RESULT_NONE) ||
        (result != DEVICE_COMMAND_RESULT_NONE && result_name == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    cJSON *root = cJSON_CreateObject();
    if (root == NULL) {
        return ESP_ERR_NO_MEM;
    }
    bool complete = cJSON_AddStringToObject(root, "type", "command_ack") != NULL &&
                    cJSON_AddNumberToObject(root, "sequence", sequence) != NULL &&
                    cJSON_AddStringToObject(root, "command_id", command_id) != NULL &&
                    cJSON_AddBoolToObject(root, "accepted", accepted) != NULL;
    if (complete && result_name != NULL) {
        complete = cJSON_AddStringToObject(root, "result", result_name) != NULL;
    }
    esp_err_t err = complete ? print_json(root, output, output_size) : ESP_ERR_NO_MEM;
    cJSON_Delete(root);
    return err;
}

esp_err_t device_protocol_encode_wake_model_status(char *output,
                                                   size_t output_size,
                                                   uint32_t sequence,
                                                   const char *job_id,
                                                   const char *status,
                                                   const char *model_name,
                                                   const char *sha256)
{
    if (output == NULL || output_size == 0 || sequence == 0 || !is_valid_uuid(job_id) ||
        !(status != NULL && (strcmp(status, "INSTALLED") == 0 ||
                             strcmp(status, "ROLLED_BACK") == 0)) ||
        !is_valid_wake_model_name(model_name) || !is_valid_sha256(sha256)) {
        return ESP_ERR_INVALID_ARG;
    }

    cJSON *root = cJSON_CreateObject();
    if (root == NULL) {
        return ESP_ERR_NO_MEM;
    }
    bool complete = cJSON_AddStringToObject(root, "type", "wake_model_status") != NULL &&
                    cJSON_AddNumberToObject(root, "sequence", sequence) != NULL &&
                    cJSON_AddStringToObject(root, "job_id", job_id) != NULL &&
                    cJSON_AddStringToObject(root, "status", status) != NULL &&
                    cJSON_AddStringToObject(root, "model_name", model_name) != NULL &&
                    cJSON_AddStringToObject(root, "sha256", sha256) != NULL;
    esp_err_t err = complete ? print_json(root, output, output_size) : ESP_ERR_NO_MEM;
    cJSON_Delete(root);
    return err;
}

esp_err_t device_protocol_encode_voice_turn_stage(char *output,
                                                  size_t output_size,
                                                  uint32_t sequence,
                                                  const char *turn_id,
                                                  device_voice_turn_stage_t stage,
                                                  uint32_t elapsed_ms,
                                                  device_voice_turn_failure_t failure)
{
    const char *stage_name = voice_stage_name(stage);
    const char *failure_name = voice_failure_name(failure);
    if (output == NULL || output_size == 0 || sequence == 0 || !is_valid_uuid(turn_id) ||
        stage_name == NULL || elapsed_ms > 300000 ||
        ((stage == DEVICE_VOICE_STAGE_FAILED) != (failure_name != NULL))) {
        return ESP_ERR_INVALID_ARG;
    }
    cJSON *root = cJSON_CreateObject();
    if (root == NULL) {
        return ESP_ERR_NO_MEM;
    }
    bool complete = cJSON_AddStringToObject(root, "type", "voice_turn_stage") != NULL &&
                    cJSON_AddNumberToObject(root, "sequence", sequence) != NULL &&
                    cJSON_AddStringToObject(root, "turn_id", turn_id) != NULL &&
                    cJSON_AddStringToObject(root, "stage", stage_name) != NULL &&
                    cJSON_AddNumberToObject(root, "elapsed_ms", elapsed_ms) != NULL;
    if (complete && failure_name != NULL) {
        complete = cJSON_AddStringToObject(root, "failure_code", failure_name) != NULL;
    }
    esp_err_t err = complete ? print_json(root, output, output_size) : ESP_ERR_NO_MEM;
    cJSON_Delete(root);
    return err;
}
