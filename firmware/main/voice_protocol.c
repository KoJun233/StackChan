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
