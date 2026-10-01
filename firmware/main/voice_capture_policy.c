#include "voice_capture_policy.h"

voice_capture_decision_t voice_capture_policy_window(
    voice_capture_policy_t *policy, uint32_t energy, uint32_t start_threshold,
    uint32_t silence_threshold, bool press_to_talk)
{
    if (!policy->speech_started) {
        if (press_to_talk) {
            uint32_t threshold = silence_threshold / 2U;
            if (threshold < 50U) threshold = 50U;
            if (threshold > 100U) threshold = 100U;
            if (energy < threshold) return VOICE_CAPTURE_WAIT;
            policy->speech_started = true;
            policy->speech_windows = VOICE_CAPTURE_PRE_ROLL_WINDOWS;
            return VOICE_CAPTURE_START;
        }
        policy->start_windows = energy >= start_threshold ? policy->start_windows + 1U : 0U;
        if (policy->start_windows < VOICE_CAPTURE_START_WINDOWS) return VOICE_CAPTURE_WAIT;
        policy->speech_started = true;
        policy->speech_windows = VOICE_CAPTURE_PRE_ROLL_WINDOWS;
        return VOICE_CAPTURE_START;
    }
    policy->speech_windows++;
    policy->silent_windows = energy <= silence_threshold ? policy->silent_windows + 1U : 0U;
    return !press_to_talk && policy->speech_windows >= VOICE_CAPTURE_MIN_WINDOWS &&
                   policy->silent_windows >= VOICE_CAPTURE_SILENCE_WINDOWS
               ? VOICE_CAPTURE_END : VOICE_CAPTURE_KEEP;
}

bool voice_capture_policy_wait_expired(const voice_capture_policy_t *policy,
                                     uint32_t elapsed_ms, uint32_t wait_ms)
{
    return !policy->speech_started && elapsed_ms >= wait_ms;
}
