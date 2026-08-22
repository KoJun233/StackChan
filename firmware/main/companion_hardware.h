#pragma once

#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"
#include "expression_engine.h"
#include "interaction_state.h"

typedef struct {
    uint8_t target_fps;
    uint8_t actual_fps;
    uint32_t draw_time_us;
    uint32_t transfer_time_us;
    uint32_t display_lock_wait_us;
    uint32_t dropped_frames;
    uint32_t audio_underruns;
    uint32_t minimum_free_heap;
    companion_expression_layer_t active_layer;
    uint8_t degrade_reason;
    bool dynamic_renderer;
    bool imu_supported;
    bool proximity_supported;
} companion_expression_diagnostics_t;

enum {
    COMPANION_EXPRESSION_DEGRADE_NONE = 0,
    COMPANION_EXPRESSION_DEGRADE_DRAW_BUDGET = 1,
    COMPANION_EXPRESSION_DEGRADE_DISPLAY_LOCK = 2,
    COMPANION_EXPRESSION_DEGRADE_AUDIO_BUSY = 3,
    COMPANION_EXPRESSION_DEGRADE_AUDIO_UNDERRUN = 4,
    COMPANION_EXPRESSION_DEGRADE_IDLE_SLEEP = 5,
};

typedef enum {
    COMPANION_EXPRESSION_FPS_FIXED = 0,
    COMPANION_EXPRESSION_FPS_ADAPTIVE,
} companion_expression_fps_mode_t;

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

/** Applies server-controlled speaker volume and display night mode. */
esp_err_t companion_hardware_configure_interaction(int volume_percent, bool night_mode);

/** Waits for a bounded touch edge emitted by the UI task. */
bool companion_hardware_wait_touch_event(companion_touch_event_t *event, uint32_t timeout_ms);

/** Applies a server-validated role color and bounded emotion suggestion. */
esp_err_t companion_hardware_configure_expression(uint32_t rgb,
                                                  companion_emotion_t emotion,
                                                  companion_emotion_intensity_t intensity,
                                                  uint32_t duration_ms);

/** Applies a server-persisted fixed target or bounded adaptive frame-rate policy. */
esp_err_t companion_hardware_configure_expression_frame_rate(
    companion_expression_fps_mode_t mode, uint8_t min_fps, uint8_t max_fps);

/** Temporarily previews one semantic without changing the real interaction state. */
esp_err_t companion_hardware_preview_expression(companion_expression_preview_t preview,
                                                uint8_t value,
                                                uint32_t duration_ms);

/** Copies privacy-safe renderer diagnostics for the next heartbeat. */
void companion_hardware_get_expression_diagnostics(companion_expression_diagnostics_t *diagnostics);

/** Gives firmware installation a deterministic highest-priority blue-violet state. */
void companion_hardware_set_expression_updating(bool updating);

/** Updates the face and records user-visible activity, exiting the screensaver. */
void companion_hardware_set_state(companion_face_state_t state);

/** Redraws the current state after a resource-pack activation or fallback. */
void companion_hardware_refresh_face(void);

/** Updates the persistent online/offline presentation without discarding the interaction phase. */
void companion_hardware_set_connected(bool connected);

/** Exits the low-brightness pupil screensaver without changing the current face state. */
void companion_hardware_mark_activity(void);

#ifdef __cplusplus
}
#endif
