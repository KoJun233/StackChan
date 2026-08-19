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
                                                          const uint8_t *payload,
                                                          size_t payload_size,
                                                          void *context);

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

esp_err_t voice_service_fetch_reminder(const device_identity_t *identity,
                                       const char *reminder_id,
                                       voice_service_buffer_t *response);

/** Cancels only the active conversational turn request; reminder downloads are unaffected. */
esp_err_t voice_service_cancel_active_turn(void);

void voice_service_release(voice_service_buffer_t *buffer);
