#include "continuous_conversation.h"

#include <ctype.h>
#include <stddef.h>
#include <string.h>

bool continuous_conversation_settings_valid(const continuous_conversation_settings_t *settings)
{
    return settings != NULL &&
           settings->follow_up_window_seconds >= CONTINUOUS_CONVERSATION_FOLLOW_UP_SECONDS_MIN &&
           settings->follow_up_window_seconds <= CONTINUOUS_CONVERSATION_FOLLOW_UP_SECONDS_MAX;
}

bool continuous_conversation_should_offer_follow_up(
    const continuous_conversation_settings_t *settings,
    uint32_t completed_follow_up_turns,
    uint32_t conversation_elapsed_ms,
    bool explicit_end,
    bool online)
{
    return continuous_conversation_settings_valid(settings) && settings->enabled && !explicit_end && online &&
           completed_follow_up_turns < CONTINUOUS_CONVERSATION_MAX_FOLLOW_UP_TURNS &&
           conversation_elapsed_ms < CONTINUOUS_CONVERSATION_MAX_DURATION_MS;
}

uint32_t continuous_conversation_capture_seconds(
    const continuous_conversation_settings_t *settings,
    uint32_t conversation_elapsed_ms)
{
    if (!continuous_conversation_settings_valid(settings) || !settings->enabled ||
        conversation_elapsed_ms >= CONTINUOUS_CONVERSATION_MAX_DURATION_MS) {
        return 0;
    }
    uint32_t remaining_seconds =
        (CONTINUOUS_CONVERSATION_MAX_DURATION_MS - conversation_elapsed_ms) / 1000U;
    return settings->follow_up_window_seconds < remaining_seconds
               ? settings->follow_up_window_seconds
               : remaining_seconds;
}

static bool is_end_punctuation(const unsigned char *value, size_t remaining, size_t *consumed)
{
    if (remaining >= 3 &&
        ((value[0] == 0xe3 && value[1] == 0x80 && (value[2] == 0x82 || value[2] == 0x81)) ||
         (value[0] == 0xef && value[1] == 0xbc &&
          (value[2] == 0x81 || value[2] == 0x8c || value[2] == 0x8e || value[2] == 0x9b ||
           value[2] == 0x9f)))) {
        *consumed = 3;
        return true;
    }
    if (remaining > 0 && strchr(".,!?;", value[0]) != NULL) {
        *consumed = 1;
        return true;
    }
    return false;
}

bool continuous_conversation_transcript_requests_end(const char *transcript)
{
    static const char phrase[] = "结束聊天";
    if (transcript == NULL) {
        return false;
    }
    const unsigned char *start = (const unsigned char *)transcript;
    while (*start != '\0' && isspace(*start)) {
        ++start;
    }
    size_t phrase_size = strlen(phrase);
    if (strlen((const char *)start) < phrase_size || memcmp(start, phrase, phrase_size) != 0) {
        return false;
    }
    const unsigned char *suffix = start + phrase_size;
    while (*suffix != '\0') {
        if (isspace(*suffix)) {
            ++suffix;
            continue;
        }
        size_t consumed = 0;
        if (!is_end_punctuation(suffix, strlen((const char *)suffix), &consumed)) {
            return false;
        }
        suffix += consumed;
    }
    return true;
}
