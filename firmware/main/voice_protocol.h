#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define VOICE_PROTOCOL_TRANSCRIPT_MAX_LEN 2048
#define VOICE_PROTOCOL_REPLY_MAX_LEN 4096
#define VOICE_PROTOCOL_METADATA_MAX_LEN 8192
#define VOICE_PROTOCOL_STREAM_MAX_AUDIO_LEN (2U * 1024U * 1024U)
#define VOICE_PROTOCOL_STREAM_MAX_SEGMENTS 8U

#define VOICE_STREAM_FRAME_START 1U
#define VOICE_STREAM_FRAME_AUDIO 2U
#define VOICE_STREAM_FRAME_COMPLETE 3U
#define VOICE_STREAM_FRAME_ERROR 4U

typedef enum {
    VOICE_STREAM_ERROR_NONE = 0,
    VOICE_STREAM_ERROR_NO_SPEECH,
    VOICE_STREAM_ERROR_CANCELLED,
    VOICE_STREAM_ERROR_LLM_UNAVAILABLE,
    VOICE_STREAM_ERROR_SPEECH_UNAVAILABLE,
    VOICE_STREAM_ERROR_INTERNAL,
} voice_stream_error_t;

typedef struct {
    char transcript[VOICE_PROTOCOL_TRANSCRIPT_MAX_LEN];
    char reply[VOICE_PROTOCOL_REPLY_MAX_LEN];
    const uint8_t *wav;
    size_t wav_size;
} voice_turn_response_t;

bool voice_protocol_parse_turn_response(const uint8_t *payload,
                                        size_t payload_size,
                                        voice_turn_response_t *response);

bool voice_protocol_parse_stream_start(const uint8_t *payload,
                                       size_t payload_size,
                                       voice_turn_response_t *response);

bool voice_protocol_parse_stream_audio(const uint8_t *payload,
                                       size_t payload_size,
                                       uint32_t expected_sequence,
                                       const uint8_t **wav,
                                       size_t *wav_size);

bool voice_protocol_parse_stream_complete(const uint8_t *payload,
                                          size_t payload_size,
                                          uint32_t expected_segment_count);

bool voice_protocol_parse_stream_error(const uint8_t *payload,
                                       size_t payload_size,
                                       voice_stream_error_t *error);
