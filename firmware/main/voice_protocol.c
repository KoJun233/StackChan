#include "voice_protocol.h"

#include <string.h>

#include "audio_wav.h"
#include "cJSON.h"
#include "strict_json.h"

static uint32_t read_be32(const uint8_t *value)
{
    return ((uint32_t)value[0] << 24) |
           ((uint32_t)value[1] << 16) |
           ((uint32_t)value[2] << 8) |
           (uint32_t)value[3];
}

static bool copy_bounded_json_string(cJSON *root, const char *name, char *output, size_t output_size)
{
    cJSON *value = cJSON_GetObjectItemCaseSensitive(root, name);
    size_t length = cJSON_IsString(value) && value->valuestring != NULL
                        ? strnlen(value->valuestring, output_size)
                        : 0;
    if (length == 0 || length >= output_size) {
        return false;
    }
    memcpy(output, value->valuestring, length + 1);
    return true;
}

bool voice_protocol_parse_turn_response(const uint8_t *payload,
                                        size_t payload_size,
                                        voice_turn_response_t *response)
{
    if (response == NULL) {
        return false;
    }
    memset(response, 0, sizeof(*response));
    if (payload == NULL || payload_size < 8 + AUDIO_WAV_HEADER_SIZE || memcmp(payload, "SCV1", 4) != 0) {
        return false;
    }

    uint32_t metadata_size = read_be32(payload + 4);
    if (metadata_size == 0 || metadata_size > VOICE_PROTOCOL_METADATA_MAX_LEN ||
        metadata_size > payload_size - 8 - AUDIO_WAV_HEADER_SIZE ||
        strict_json_contains_decoded_nul_escape((const char *)payload + 8, metadata_size)) {
        return false;
    }

    const char *parse_end = NULL;
    const char *metadata = (const char *)payload + 8;
    cJSON *root = cJSON_ParseWithLengthOpts(metadata, metadata_size, &parse_end, false);
    bool metadata_valid = root != NULL && cJSON_IsObject(root) && cJSON_GetArraySize(root) == 2 &&
                          strict_json_has_only_trailing_whitespace(metadata, metadata_size, parse_end) &&
                          copy_bounded_json_string(root, "transcript", response->transcript,
                                                   sizeof(response->transcript)) &&
                          copy_bounded_json_string(root, "reply", response->reply, sizeof(response->reply));
    cJSON_Delete(root);
    if (!metadata_valid) {
        memset(response, 0, sizeof(*response));
        return false;
    }

    response->wav = payload + 8 + metadata_size;
    response->wav_size = payload_size - 8 - metadata_size;
    audio_wav_view_t wav_view = {0};
    if (!audio_wav_parse(response->wav, response->wav_size, &wav_view)) {
        memset(response, 0, sizeof(*response));
        return false;
    }
    return true;
}

static cJSON *parse_strict_object(const uint8_t *payload, size_t payload_size, size_t field_count)
{
    if (payload == NULL || payload_size == 0 || payload_size > VOICE_PROTOCOL_METADATA_MAX_LEN ||
        strict_json_contains_decoded_nul_escape((const char *)payload, payload_size)) {
        return NULL;
    }
    const char *parse_end = NULL;
    cJSON *root = cJSON_ParseWithLengthOpts((const char *)payload, payload_size, &parse_end, false);
    if (root == NULL || !cJSON_IsObject(root) || cJSON_GetArraySize(root) != field_count ||
        !strict_json_has_only_trailing_whitespace((const char *)payload, payload_size, parse_end)) {
        cJSON_Delete(root);
        return NULL;
    }
    return root;
}

bool voice_protocol_parse_stream_start(const uint8_t *payload,
                                       size_t payload_size,
                                       voice_turn_response_t *response)
{
    if (response == NULL) return false;
    memset(response, 0, sizeof(*response));
    cJSON *root = parse_strict_object(payload, payload_size, 1);
    bool valid = root != NULL && copy_bounded_json_string(
        root, "transcript", response->transcript, sizeof(response->transcript));
    cJSON_Delete(root);
    if (!valid) memset(response, 0, sizeof(*response));
    return valid;
}

bool voice_protocol_parse_stream_audio(const uint8_t *payload,
                                       size_t payload_size,
                                       uint32_t expected_sequence,
                                       const uint8_t **wav,
                                       size_t *wav_size)
{
    if (payload == NULL || wav == NULL || wav_size == NULL ||
        payload_size < sizeof(uint32_t) + AUDIO_WAV_HEADER_SIZE ||
        payload_size > sizeof(uint32_t) + VOICE_PROTOCOL_STREAM_MAX_AUDIO_LEN ||
        read_be32(payload) != expected_sequence) {
        return false;
    }
    const uint8_t *audio = payload + sizeof(uint32_t);
    size_t audio_size = payload_size - sizeof(uint32_t);
    audio_wav_view_t view = {0};
    if (!audio_wav_parse(audio, audio_size, &view)) return false;
    *wav = audio;
    *wav_size = audio_size;
    return true;
}

bool voice_protocol_parse_stream_complete(const uint8_t *payload,
                                          size_t payload_size,
                                          uint32_t expected_segment_count)
{
    if (expected_segment_count == 0 || expected_segment_count > VOICE_PROTOCOL_STREAM_MAX_SEGMENTS) {
        return false;
    }
    cJSON *root = parse_strict_object(payload, payload_size, 1);
    cJSON *count = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root, "segmentCount");
    bool valid = root != NULL && cJSON_IsNumber(count) && count->valuedouble == count->valueint &&
                 count->valueint == (int)expected_segment_count;
    cJSON_Delete(root);
    return valid;
}

bool voice_protocol_parse_stream_error(const uint8_t *payload,
                                       size_t payload_size,
                                       voice_stream_error_t *error)
{
    if (error == NULL) return false;
    *error = VOICE_STREAM_ERROR_NONE;
    cJSON *root = parse_strict_object(payload, payload_size, 1);
    cJSON *code = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root, "code");
    if (!cJSON_IsString(code) || code->valuestring == NULL) {
        cJSON_Delete(root);
        return false;
    }
    if (strcmp(code->valuestring, "no_speech") == 0) {
        *error = VOICE_STREAM_ERROR_NO_SPEECH;
    } else if (strcmp(code->valuestring, "cancelled") == 0) {
        *error = VOICE_STREAM_ERROR_CANCELLED;
    } else if (strcmp(code->valuestring, "llm_unavailable") == 0) {
        *error = VOICE_STREAM_ERROR_LLM_UNAVAILABLE;
    } else if (strcmp(code->valuestring, "speech_unavailable") == 0) {
        *error = VOICE_STREAM_ERROR_SPEECH_UNAVAILABLE;
    } else if (strcmp(code->valuestring, "internal_error") == 0) {
        *error = VOICE_STREAM_ERROR_INTERNAL;
    }
    cJSON_Delete(root);
    return *error != VOICE_STREAM_ERROR_NONE;
}
