#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define VOICE_PROTOCOL_TRANSCRIPT_MAX_LEN 2048
#define VOICE_PROTOCOL_REPLY_MAX_LEN 4096
#define VOICE_PROTOCOL_METADATA_MAX_LEN 8192

typedef struct {
    char transcript[VOICE_PROTOCOL_TRANSCRIPT_MAX_LEN];
    char reply[VOICE_PROTOCOL_REPLY_MAX_LEN];
    const uint8_t *wav;
    size_t wav_size;
} voice_turn_response_t;

bool voice_protocol_parse_turn_response(const uint8_t *payload,
                                        size_t payload_size,
                                        voice_turn_response_t *response);
