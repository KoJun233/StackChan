#pragma once

#include <stdbool.h>
#include <stdint.h>

#define VOICE_CAPTURE_PRE_ROLL_WINDOWS 3U
#define VOICE_CAPTURE_START_WINDOWS 2U
#define VOICE_CAPTURE_MIN_WINDOWS 10U
#define VOICE_CAPTURE_SILENCE_WINDOWS 8U

typedef enum {
    VOICE_CAPTURE_WAIT = 0,
    VOICE_CAPTURE_START,
    VOICE_CAPTURE_KEEP,
    VOICE_CAPTURE_END,
} voice_capture_decision_t;

typedef struct {
    uint32_t start_windows;
    uint32_t silent_windows;
    uint32_t speech_windows;
    bool speech_started;
} voice_capture_policy_t;

voice_capture_decision_t voice_capture_policy_window(
    voice_capture_policy_t *policy, uint32_t energy, uint32_t start_threshold,
    uint32_t silence_threshold, bool press_to_talk);

bool voice_capture_policy_wait_expired(const voice_capture_policy_t *policy,
                                     uint32_t elapsed_ms, uint32_t wait_ms);
