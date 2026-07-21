package com.kj.stackchan.speech;

interface SpeechProviderAdapter {

    String transcribe(ResolvedSpeechSettings settings, byte[] wavAudio);

    default String transcribeSynthesized(ResolvedSpeechSettings settings, byte[] wavAudio) {
        return transcribe(settings, wavAudio);
    }

    byte[] synthesize(ResolvedSpeechSettings settings, String text);
}
