#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#define AUDIO_WAV_HEADER_SIZE 44

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    const uint8_t *data;
    size_t data_size;
    uint32_t sample_rate;
    uint16_t channels;
    uint16_t bits_per_sample;
} audio_wav_view_t;

esp_err_t audio_wav_build_pcm16_mono(uint8_t *output,
                                     size_t output_size,
                                     const int16_t *samples,
                                     size_t sample_count,
                                     uint32_t sample_rate,
                                     size_t *wav_size);

bool audio_wav_parse(const uint8_t *wav, size_t wav_size, audio_wav_view_t *view);

#ifdef __cplusplus
}
#endif
