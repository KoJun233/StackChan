#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "esp_err.h"

#include "device_identity.h"

typedef enum {
    VOICE_WAKE_SENSITIVITY_NORMAL = 0,
    VOICE_WAKE_SENSITIVITY_SENSITIVE,
} voice_wake_sensitivity_t;

/** Starts the local WakeNet listener and bounded voice-turn task. */
esp_err_t voice_control_start(void);

/** Applies bounded wake and local speech-detection settings without restarting the device. */
esp_err_t voice_control_configure(voice_wake_sensitivity_t wake_sensitivity,
                                  uint32_t speech_start_threshold,
                                  uint32_t speech_silence_threshold);

/** Cancels the active voice turn and stops any current playback. */
void voice_control_cancel_active_turn(void);

/** Fetches the fixed same-origin reminder WAV and plays it synchronously. */
esp_err_t voice_control_play_reminder(const device_identity_t *identity,
                                      const char *reminder_id,
                                      bool *cancelled);
