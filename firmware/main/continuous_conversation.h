#pragma once

#include <stdbool.h>
#include <stdint.h>

#define CONTINUOUS_CONVERSATION_FOLLOW_UP_SECONDS_DEFAULT 8U
#define CONTINUOUS_CONVERSATION_FOLLOW_UP_SECONDS_MIN 3U
#define CONTINUOUS_CONVERSATION_FOLLOW_UP_SECONDS_MAX 8U
#define CONTINUOUS_CONVERSATION_MAX_FOLLOW_UP_TURNS 3U
#define CONTINUOUS_CONVERSATION_MAX_DURATION_MS 120000U

typedef struct {
    bool enabled;
    uint32_t follow_up_window_seconds;
} continuous_conversation_settings_t;

bool continuous_conversation_settings_valid(const continuous_conversation_settings_t *settings);

bool continuous_conversation_should_offer_follow_up(
    const continuous_conversation_settings_t *settings,
    uint32_t completed_follow_up_turns,
    uint32_t conversation_elapsed_ms,
    bool explicit_end,
    bool online);

uint32_t continuous_conversation_capture_seconds(
    const continuous_conversation_settings_t *settings,
    uint32_t conversation_elapsed_ms);

bool continuous_conversation_transcript_requests_end(const char *transcript);
