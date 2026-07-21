package com.kj.stackchan.speech;

public record ResolvedSpeechSettings(
        SpeechProviderType providerType,
        String baseUrl,
        String workspaceId,
        String asrModel,
        SpeechAccessMode asrMode,
        String ttsModel,
        SpeechAccessMode ttsMode,
        String ttsVoice,
        String apiKey
) {
}
