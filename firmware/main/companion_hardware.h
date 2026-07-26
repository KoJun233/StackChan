#pragma once

#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"
#include "interaction_state.h"

typedef enum {
    COMPANION_TOUCH_PRESSED = 0,
    COMPANION_TOUCH_RELEASED,
} companion_touch_event_type_t;

typedef struct {
    companion_touch_event_type_t type;
    int64_t occurred_us;
} companion_touch_event_t;

#ifdef __cplusplus
extern "C" {
#endif

/** Initializes only the CoreS3 display, touch, microphone, and speaker. */
esp_err_t companion_hardware_init(void);

/** Records bounded 16-bit mono PCM while serializing microphone/speaker access. */
esp_err_t companion_hardware_record_pcm(int16_t *samples,
                                        size_t sample_count,
                                        uint32_t sample_rate);

/** Validates and plays an in-memory PCM WAV, then resumes microphone capture. */
esp_err_t companion_hardware_play_wav(const uint8_t *wav, size_t wav_size);

/** Plays WAV audio while honoring a local stop request and reports user cancellation separately. */
esp_err_t companion_hardware_play_wav_interruptible(const uint8_t *wav,
                                                    size_t wav_size,
                                                    bool *cancelled);

/** Requests that active speaker playback stop; safe to call from the touch-control task. */
void companion_hardware_request_playback_stop(void);

/** Waits for a bounded touch edge emitted by the UI task. */
bool companion_hardware_wait_touch_event(companion_touch_event_t *event, uint32_t timeout_ms);

/** Updates the face and records user-visible activity, exiting the screensaver. */
void companion_hardware_set_state(companion_face_state_t state);

/** Updates the persistent online/offline presentation without discarding the interaction phase. */
void companion_hardware_set_connected(bool connected);

/** Exits the low-brightness pupil screensaver without changing the current face state. */
void companion_hardware_mark_activity(void);

#ifdef __cplusplus
}
#endif
