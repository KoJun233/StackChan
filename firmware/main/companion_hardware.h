#pragma once

#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    COMPANION_FACE_IDLE = 0,
    COMPANION_FACE_LISTENING,
    COMPANION_FACE_THINKING,
    COMPANION_FACE_SPEAKING,
    COMPANION_FACE_ERROR,
} companion_face_state_t;

/** Initializes only the CoreS3 display, touch, microphone, and speaker. */
esp_err_t companion_hardware_init(void);

/** Records bounded 16-bit mono PCM while serializing microphone/speaker access. */
esp_err_t companion_hardware_record_pcm(int16_t *samples,
                                        size_t sample_count,
                                        uint32_t sample_rate);

/** Validates and plays an in-memory PCM WAV, then resumes microphone capture. */
esp_err_t companion_hardware_play_wav(const uint8_t *wav, size_t wav_size);

/** Updates the face and records user-visible activity, exiting the screensaver. */
void companion_hardware_set_state(companion_face_state_t state);

/** Exits the low-brightness pupil screensaver without changing the current face state. */
void companion_hardware_mark_activity(void);

#ifdef __cplusplus
}
#endif
