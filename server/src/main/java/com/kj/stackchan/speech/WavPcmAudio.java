package com.kj.stackchan.speech;

import java.util.Arrays;

final class WavPcmAudio {

    private static final int REQUIRED_SAMPLE_RATE = 16000;
    private static final int CANONICAL_WAV_HEADER_SIZE = 44;
    private static final String INVALID_WAV = "WAV audio must be 16 kHz, 16-bit, mono PCM";

    private WavPcmAudio() {
    }

    static byte[] extractMono16KhzPcm(byte[] wav) {
        return extractMono16KhzPcm(wav, false);
    }

    static byte[] extractMono16KhzPcm(byte[] wav, boolean allowFinalDataLengthPlaceholder) {
        if (wav == null || wav.length < 44
                || !matches(wav, 0, "RIFF") || !matches(wav, 8, "WAVE")) {
            throw new VoiceInputException(INVALID_WAV);
        }

        boolean validFormat = false;
        int dataOffset = -1;
        int dataLength = -1;
        int offset = 12;
        while (offset <= wav.length - 8) {
            int chunkLength = readLittleEndianInt(wav, offset + 4);
            int payloadOffset = offset + 8;
            boolean dataChunk = matches(wav, offset, "data");
            int remainingLength = wav.length - payloadOffset;
            if (dataChunk && (chunkLength == -1
                    || (allowFinalDataLengthPlaceholder && chunkLength > remainingLength))) {
                chunkLength = wav.length - payloadOffset;
            }
            if (chunkLength < 0 || chunkLength > wav.length - offset - 8) {
                throw new VoiceInputException(INVALID_WAV);
            }
            if (matches(wav, offset, "fmt ")) {
                if (chunkLength < 16) {
                    throw new VoiceInputException(INVALID_WAV);
                }
                int audioFormat = readLittleEndianShort(wav, payloadOffset);
                int channels = readLittleEndianShort(wav, payloadOffset + 2);
                int sampleRate = readLittleEndianInt(wav, payloadOffset + 4);
                int bitsPerSample = readLittleEndianShort(wav, payloadOffset + 14);
                validFormat = audioFormat == 1 && channels == 1
                        && sampleRate == REQUIRED_SAMPLE_RATE && bitsPerSample == 16;
            } else if (dataChunk) {
                dataOffset = payloadOffset;
                dataLength = chunkLength;
            }
            offset = payloadOffset + chunkLength + (chunkLength & 1);
        }

        if (!validFormat || dataOffset < 0 || dataLength <= 0 || (dataLength & 1) != 0) {
            throw new VoiceInputException(INVALID_WAV);
        }
        return Arrays.copyOfRange(wav, dataOffset, dataOffset + dataLength);
    }

    static byte[] normalizeSynthesizedMono16KhzWav(byte[] wav) {
        byte[] pcm = extractMono16KhzPcm(wav, true);
        byte[] normalized = new byte[CANONICAL_WAV_HEADER_SIZE + pcm.length];
        writeAscii(normalized, 0, "RIFF");
        writeLittleEndianInt(normalized, 4, normalized.length - 8);
        writeAscii(normalized, 8, "WAVE");
        writeAscii(normalized, 12, "fmt ");
        writeLittleEndianInt(normalized, 16, 16);
        writeLittleEndianShort(normalized, 20, 1);
        writeLittleEndianShort(normalized, 22, 1);
        writeLittleEndianInt(normalized, 24, REQUIRED_SAMPLE_RATE);
        writeLittleEndianInt(normalized, 28, REQUIRED_SAMPLE_RATE * 2);
        writeLittleEndianShort(normalized, 32, 2);
        writeLittleEndianShort(normalized, 34, 16);
        writeAscii(normalized, 36, "data");
        writeLittleEndianInt(normalized, 40, pcm.length);
        System.arraycopy(pcm, 0, normalized, CANONICAL_WAV_HEADER_SIZE, pcm.length);
        return normalized;
    }

    static String layoutDiagnosticCode(byte[] wav) {
        if (wav == null || wav.length < 44
                || !matches(wav, 0, "RIFF") || !matches(wav, 8, "WAVE")) {
            return "header_invalid";
        }

        int audioFormat = -1;
        int channels = -1;
        int sampleRate = -1;
        int bitsPerSample = -1;
        int dataLength = -1;
        int offset = 12;
        while (offset <= wav.length - 8) {
            int chunkLength;
            try {
                chunkLength = readLittleEndianInt(wav, offset + 4);
            } catch (VoiceInputException exception) {
                return "chunk_invalid";
            }
            int payloadOffset = offset + 8;
            boolean dataChunk = matches(wav, offset, "data");
            if (dataChunk && chunkLength == -1) {
                chunkLength = wav.length - payloadOffset;
            }
            if (chunkLength < 0 || chunkLength > wav.length - offset - 8) {
                return "chunk_" + chunkName(wav, offset)
                        + "_l" + Integer.toUnsignedLong(chunkLength)
                        + "_r" + (wav.length - payloadOffset);
            }
            if (matches(wav, offset, "fmt ")) {
                if (chunkLength < 16) {
                    return "fmt_invalid";
                }
                audioFormat = readLittleEndianShort(wav, payloadOffset);
                channels = readLittleEndianShort(wav, payloadOffset + 2);
                sampleRate = readLittleEndianInt(wav, payloadOffset + 4);
                bitsPerSample = readLittleEndianShort(wav, payloadOffset + 14);
            } else if (dataChunk) {
                dataLength = chunkLength;
            }
            offset = payloadOffset + chunkLength + (chunkLength & 1);
        }

        if (audioFormat < 0) {
            return "fmt_missing";
        }
        if (dataLength < 0) {
            return "data_missing";
        }
        if (dataLength == 0) {
            return "data_empty";
        }
        if ((dataLength & 1) != 0) {
            return "data_odd";
        }
        return "f" + nonNegative(audioFormat)
                + "_c" + nonNegative(channels)
                + "_r" + nonNegative(sampleRate)
                + "_b" + nonNegative(bitsPerSample);
    }

    private static int nonNegative(int value) {
        return Math.max(value, 0);
    }

    private static String chunkName(byte[] wav, int offset) {
        StringBuilder name = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            int value = Byte.toUnsignedInt(wav[offset + index]);
            if (value >= 'A' && value <= 'Z') {
                value += 'a' - 'A';
            }
            if ((value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')) {
                name.append((char) value);
            } else {
                name.append('x');
            }
        }
        return name.toString();
    }

    private static boolean matches(byte[] input, int offset, String value) {
        if (offset < 0 || offset + value.length() > input.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (input[offset + index] != value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int readLittleEndianShort(byte[] input, int offset) {
        if (offset < 0 || offset + 2 > input.length) {
            throw new VoiceInputException(INVALID_WAV);
        }
        return Byte.toUnsignedInt(input[offset]) | Byte.toUnsignedInt(input[offset + 1]) << 8;
    }

    private static int readLittleEndianInt(byte[] input, int offset) {
        if (offset < 0 || offset + 4 > input.length) {
            throw new VoiceInputException(INVALID_WAV);
        }
        return Byte.toUnsignedInt(input[offset])
                | Byte.toUnsignedInt(input[offset + 1]) << 8
                | Byte.toUnsignedInt(input[offset + 2]) << 16
                | Byte.toUnsignedInt(input[offset + 3]) << 24;
    }

    private static void writeAscii(byte[] output, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            output[offset + index] = (byte) value.charAt(index);
        }
    }

    private static void writeLittleEndianShort(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
    }

    private static void writeLittleEndianInt(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
        output[offset + 2] = (byte) (value >>> 16);
        output[offset + 3] = (byte) (value >>> 24);
    }
}
