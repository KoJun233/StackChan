package com.kj.stackchan.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavPcmAudioTest {

    @Test
    void extractsCanonicalMono16KhzPcm() {
        byte[] pcm = new byte[] {1, 2, 3, 4};

        assertThat(WavPcmAudio.extractMono16KhzPcm(wav(pcm, 16000, 1, 16))).isEqualTo(pcm);
    }

    @Test
    void rejectsUnsupportedAudioLayouts() {
        byte[] unsupported = wav(new byte[] {1, 2}, 44100, 2, 16);

        assertThatThrownBy(() -> WavPcmAudio.extractMono16KhzPcm(unsupported))
                .isInstanceOf(VoiceInputException.class);
        assertThat(WavPcmAudio.layoutDiagnosticCode(unsupported))
                .isEqualTo("f1_c2_r44100_b16");
    }

    @Test
    void acceptsStreamingLengthSentinelForTheFinalDataChunk() {
        byte[] pcm = new byte[] {1, 2, 3, 4};
        byte[] wav = wav(pcm, 16000, 1, 16);
        writeInt(wav, 40, -1);

        assertThat(WavPcmAudio.extractMono16KhzPcm(wav)).isEqualTo(pcm);
        assertThat(WavPcmAudio.layoutDiagnosticCode(wav)).isEqualTo("f1_c1_r16000_b16");
    }

    @Test
    void describesOversizedChunksWithoutIncludingAudioContent() {
        byte[] wav = wav(new byte[] {1, 2, 3, 4}, 16000, 1, 16);
        writeInt(wav, 40, 1024);

        assertThatThrownBy(() -> WavPcmAudio.extractMono16KhzPcm(wav))
                .isInstanceOf(VoiceInputException.class);
        assertThat(WavPcmAudio.extractMono16KhzPcm(wav, true))
                .isEqualTo(new byte[] {1, 2, 3, 4});
        assertThat(WavPcmAudio.layoutDiagnosticCode(wav))
                .isEqualTo("chunk_data_l1024_r4");
    }

    @Test
    void normalizesSynthesizedProviderPlaceholdersForStrictDevicePlayback() {
        byte[] pcm = new byte[] {1, 2, 3, 4};
        byte[] providerWav = wav(pcm, 16000, 1, 16);
        writeInt(providerWav, 4, Integer.MAX_VALUE);
        writeInt(providerWav, 40, 1024);

        byte[] normalized = WavPcmAudio.normalizeSynthesizedMono16KhzWav(providerWav);

        assertThat(normalized).hasSize(44 + pcm.length);
        assertThat(readInt(normalized, 4)).isEqualTo(normalized.length - 8);
        assertThat(readInt(normalized, 40)).isEqualTo(pcm.length);
        assertThat(WavPcmAudio.extractMono16KhzPcm(normalized)).isEqualTo(pcm);
    }

    @Test
    void resamplesSynthesized24KhzMonoPcmForStrictDevicePlayback() {
        byte[] pcm = constantPcm(6, 1200);

        byte[] normalized = WavPcmAudio.normalizeSynthesizedMono16KhzWav(
                wav(pcm, 24000, 1, 16)
        );

        assertThat(normalized).hasSize(44 + 4 * 2);
        assertThat(readInt(normalized, 24)).isEqualTo(16000);
        assertThat(WavPcmAudio.extractMono16KhzPcm(normalized))
                .isEqualTo(constantPcm(4, 1200));
        assertThatThrownBy(() -> WavPcmAudio.extractMono16KhzPcm(wav(pcm, 24000, 1, 16)))
                .isInstanceOf(VoiceInputException.class);
    }

    @Test
    void rejectsUnapprovedSynthesizedSampleRates() {
        byte[] providerWav = wav(constantPcm(6, 1200), 22050, 1, 16);

        assertThatThrownBy(() -> WavPcmAudio.normalizeSynthesizedMono16KhzWav(providerWav))
                .isInstanceOf(VoiceInputException.class);
    }

    private byte[] wav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        byte[] wav = new byte[44 + pcm.length];
        writeAscii(wav, 0, "RIFF");
        writeInt(wav, 4, wav.length - 8);
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeInt(wav, 16, 16);
        writeShort(wav, 20, 1);
        writeShort(wav, 22, channels);
        writeInt(wav, 24, sampleRate);
        writeInt(wav, 28, sampleRate * channels * bitsPerSample / 8);
        writeShort(wav, 32, channels * bitsPerSample / 8);
        writeShort(wav, 34, bitsPerSample);
        writeAscii(wav, 36, "data");
        writeInt(wav, 40, pcm.length);
        System.arraycopy(pcm, 0, wav, 44, pcm.length);
        return wav;
    }

    private byte[] constantPcm(int samples, int value) {
        byte[] pcm = new byte[samples * 2];
        for (int index = 0; index < samples; index++) {
            writeShort(pcm, index * 2, value);
        }
        return pcm;
    }

    private void writeAscii(byte[] output, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            output[offset + index] = (byte) value.charAt(index);
        }
    }

    private void writeShort(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
    }

    private void writeInt(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
        output[offset + 2] = (byte) (value >>> 16);
        output[offset + 3] = (byte) (value >>> 24);
    }

    private int readInt(byte[] input, int offset) {
        return Byte.toUnsignedInt(input[offset])
                | Byte.toUnsignedInt(input[offset + 1]) << 8
                | Byte.toUnsignedInt(input[offset + 2]) << 16
                | Byte.toUnsignedInt(input[offset + 3]) << 24;
    }
}
