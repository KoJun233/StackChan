#include "audio_wav.h"

#include <limits.h>
#include <string.h>

static uint16_t read_le16(const uint8_t *value)
{
    return (uint16_t)value[0] | (uint16_t)((uint16_t)value[1] << 8);
}

static uint32_t read_le32(const uint8_t *value)
{
    return (uint32_t)value[0] |
           ((uint32_t)value[1] << 8) |
           ((uint32_t)value[2] << 16) |
           ((uint32_t)value[3] << 24);
}

static void write_le16(uint8_t *output, uint16_t value)
{
    output[0] = (uint8_t)(value & 0xff);
    output[1] = (uint8_t)(value >> 8);
}

static void write_le32(uint8_t *output, uint32_t value)
{
    output[0] = (uint8_t)(value & 0xff);
    output[1] = (uint8_t)((value >> 8) & 0xff);
    output[2] = (uint8_t)((value >> 16) & 0xff);
    output[3] = (uint8_t)(value >> 24);
}

esp_err_t audio_wav_build_pcm16_mono(uint8_t *output,
                                     size_t output_size,
                                     const int16_t *samples,
                                     size_t sample_count,
                                     uint32_t sample_rate,
                                     size_t *wav_size)
{
    if (output == NULL || samples == NULL || wav_size == NULL || sample_count == 0 ||
        sample_rate < 8000 || sample_rate > 48000 || sample_count > UINT32_MAX / sizeof(int16_t)) {
        return ESP_ERR_INVALID_ARG;
    }
    size_t data_size = sample_count * sizeof(int16_t);
    if (data_size > UINT32_MAX - 36 || output_size < AUDIO_WAV_HEADER_SIZE + data_size) {
        return ESP_ERR_NO_MEM;
    }

    memcpy(output, "RIFF", 4);
    write_le32(output + 4, (uint32_t)(36 + data_size));
    memcpy(output + 8, "WAVE", 4);
    memcpy(output + 12, "fmt ", 4);
    write_le32(output + 16, 16);
    write_le16(output + 20, 1);
    write_le16(output + 22, 1);
    write_le32(output + 24, sample_rate);
    write_le32(output + 28, sample_rate * sizeof(int16_t));
    write_le16(output + 32, sizeof(int16_t));
    write_le16(output + 34, 16);
    memcpy(output + 36, "data", 4);
    write_le32(output + 40, (uint32_t)data_size);
    memcpy(output + AUDIO_WAV_HEADER_SIZE, samples, data_size);
    *wav_size = AUDIO_WAV_HEADER_SIZE + data_size;
    return ESP_OK;
}

bool audio_wav_parse(const uint8_t *wav, size_t wav_size, audio_wav_view_t *view)
{
    if (wav == NULL || view == NULL || wav_size < AUDIO_WAV_HEADER_SIZE ||
        memcmp(wav, "RIFF", 4) != 0 || memcmp(wav + 8, "WAVE", 4) != 0) {
        return false;
    }

    uint32_t riff_size = read_le32(wav + 4);
    if (riff_size < 36 || riff_size > wav_size - 8) {
        return false;
    }
    size_t riff_end = 8 + (size_t)riff_size;

    memset(view, 0, sizeof(*view));
    bool found_format = false;
    size_t offset = 12;
    while (offset <= riff_end - 8) {
        const uint8_t *chunk = wav + offset;
        uint32_t chunk_size = read_le32(chunk + 4);
        size_t data_offset = offset + 8;
        if (chunk_size > riff_end - data_offset) {
            return false;
        }
        if (memcmp(chunk, "fmt ", 4) == 0) {
            if (chunk_size < 16 || read_le16(wav + data_offset) != 1) {
                return false;
            }
            view->channels = read_le16(wav + data_offset + 2);
            view->sample_rate = read_le32(wav + data_offset + 4);
            view->bits_per_sample = read_le16(wav + data_offset + 14);
            if ((view->channels != 1 && view->channels != 2) ||
                (view->bits_per_sample != 8 && view->bits_per_sample != 16) ||
                view->sample_rate < 8000 || view->sample_rate > 48000) {
                return false;
            }
            found_format = true;
        } else if (memcmp(chunk, "data", 4) == 0) {
            if (!found_format || chunk_size == 0) {
                return false;
            }
            size_t bytes_per_frame = (size_t)view->channels * (view->bits_per_sample / 8U);
            if (bytes_per_frame == 0 || chunk_size % bytes_per_frame != 0) {
                return false;
            }
            view->data = wav + data_offset;
            view->data_size = chunk_size;
            return true;
        }

        size_t padded_size = (size_t)chunk_size + ((size_t)chunk_size & 1U);
        if (padded_size > riff_end - data_offset) {
            return false;
        }
        offset = data_offset + padded_size;
    }
    return false;
}
