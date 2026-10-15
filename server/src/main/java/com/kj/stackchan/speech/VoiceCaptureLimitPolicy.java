package com.kj.stackchan.speech;

/** The payload bound is a conservative action guard, never device timing telemetry. */
public final class VoiceCaptureLimitPolicy {
    private static final int MAX_PCM_BYTES = 16_000 * 8 * 2;

    private VoiceCaptureLimitPolicy() { }

    public static boolean requiresClarification(byte[] wav, boolean live) {
        try {
            return WavPcmAudio.extractMono16KhzPcm(wav, live).length >= MAX_PCM_BYTES;
        } catch (VoiceInputException exception) {
            // Preserve existing provider/input validation and its fixed failure code.
            return false;
        }
    }
}
