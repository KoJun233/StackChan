#pragma once
#include <stdbool.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint32_t stable_since_ms;
    uint32_t audio_errors;
    uint32_t audio_error_ms;
    uint8_t over_budget_frames;
    bool audio_guard;
} display_frame_policy_t;

/** Advance on the original cadence; skip missed slots, never burst to catch up. */
int64_t display_frame_next_deadline(int64_t deadline_us, int64_t finished_us,
    uint8_t fps, uint32_t *skipped);
/** Audio playback alone is not a reason to slow down. Completed work and actual
 * audio errors control adaptive reduction, with a ten-second recovery window. */
uint8_t display_frame_adapt(display_frame_policy_t *policy, uint32_t now_ms,
    uint8_t target, uint8_t minimum, uint8_t maximum, bool fixed, bool rendered,
    uint32_t work_us, uint32_t lock_us, uint32_t audio_errors, uint8_t *reason);

#ifdef __cplusplus
}
#endif
