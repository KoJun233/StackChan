#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#include "device_identity.h"

typedef struct {
    uint8_t *data;
    size_t size;
} voice_service_buffer_t;

typedef esp_err_t (*voice_service_stream_frame_handler_t)(uint8_t frame_type,
                                                          uint8_t *payload,
                                                          size_t payload_size,
                                                          // Set true only when the handler assumes ownership.
                                                          bool *retain_payload,
                                                          void *context);

typedef struct voice_service_live_upload voice_service_live_upload_t;

/** Initializes the cross-task cancellation guard before voice traffic starts. */
esp_err_t voice_service_init(void);

esp_err_t voice_service_send_turn(const device_identity_t *identity,
                                  const char *turn_id,
                                  const uint8_t *wav,
                                  size_t wav_size,
                                  voice_service_buffer_t *response);

esp_err_t voice_service_send_turn_streaming(const device_identity_t *identity,
                                            const char *turn_id,
                                            const uint8_t *wav,
                                            size_t wav_size,
                                            voice_service_stream_frame_handler_t frame_handler,
                                            void *frame_context,
                                            voice_service_buffer_t *legacy_response,
                                            bool *streamed);

/** Opens a bounded chunked WAV request after local VAD has detected speech. */
esp_err_t voice_service_live_upload_begin(const device_identity_t *identity,
                                          const char *turn_id,
                                          uint32_t sample_rate,
                                          voice_service_stream_frame_handler_t frame_handler,
                                          void *frame_context,
                                          voice_service_live_upload_t **upload);

/** Writes captured PCM without increasing the 1 KiB internal network-buffer budget. */
esp_err_t voice_service_live_upload_write(voice_service_live_upload_t *upload,
                                          const int16_t *samples,
                                          size_t sample_count);

/** Records the physical capture deadline so queued upload tail remains measurable. */
void voice_service_live_upload_mark_capture_finished_at(voice_service_live_upload_t *upload,
                                                        int64_t capture_finished_us);

/** Terminates the chunked request and consumes the existing SCV2/SCV1 response. */
esp_err_t voice_service_live_upload_finish(voice_service_live_upload_t *upload,
                                           voice_service_buffer_t *legacy_response,
                                           bool *streamed);

/** Aborts an incomplete live upload; safe to call after a failed begin/write. */
void voice_service_live_upload_abort(voice_service_live_upload_t *upload);

esp_err_t voice_service_fetch_reminder(const device_identity_t *identity,
                                       const char *reminder_id,
                                       voice_service_buffer_t *response);

/** Cancels only the active conversational turn request; reminder downloads are unaffected. */
esp_err_t voice_service_cancel_active_turn(void);

void voice_service_release(voice_service_buffer_t *buffer);
