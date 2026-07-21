#pragma once

#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#include "device_identity.h"

typedef struct {
    uint8_t *data;
    size_t size;
} voice_service_buffer_t;

esp_err_t voice_service_send_turn(const device_identity_t *identity,
                                  const uint8_t *wav,
                                  size_t wav_size,
                                  voice_service_buffer_t *response);

esp_err_t voice_service_fetch_reminder(const device_identity_t *identity,
                                       const char *reminder_id,
                                       voice_service_buffer_t *response);

void voice_service_release(voice_service_buffer_t *buffer);
