#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "esp_err.h"

#include "device_identity.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    VOICE_WAKE_SENSITIVITY_NORMAL = 0,
    VOICE_WAKE_SENSITIVITY_SENSITIVE,
} voice_wake_sensitivity_t;

/** Reserves voice tasks before Wi-Fi without loading WakeNet or recording. */
esp_err_t voice_control_start(void);
/** Opens the reserved voice worker after network and critical UI startup. */
void voice_control_activate(void);
/** Local face/menu selection, persisted before reporting success. */
esp_err_t voice_control_set_input_mode(bool automatic_wake);
bool voice_control_is_automatic_wake(void);

/** Applies bounded wake and local speech-detection settings without restarting the device. */
esp_err_t voice_control_configure(voice_wake_sensitivity_t wake_sensitivity,
                                  uint32_t speech_start_threshold,
                                  uint32_t speech_silence_threshold);

/** Applies the bounded local follow-up window used after successful replies. */
esp_err_t voice_control_configure_continuous_conversation(bool enabled,
                                                          uint32_t follow_up_window_seconds);

/** Cancels the active voice turn and stops any current playback. */
void voice_control_cancel_active_turn(void);

/** Returns true while a user voice turn must take priority over body movement. */
bool voice_control_motion_blocked(void);

/** Pauses passive WakeNet sampling before calibration or fixed motion; fails during an active voice turn. */
bool voice_control_pause_wake_for_body_action(void);

/** Restores passive WakeNet sampling after the body action finishes or is rejected. */
void voice_control_resume_wake_after_body_action(void);

/** Fetches the fixed same-origin reminder WAV and plays it synchronously. */
esp_err_t voice_control_play_reminder(const device_identity_t *identity,
                                      const char *reminder_id,
                                      bool *cancelled);

#ifdef __cplusplus
}
#endif
