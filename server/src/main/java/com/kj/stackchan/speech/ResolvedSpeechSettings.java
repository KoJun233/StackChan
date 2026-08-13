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
    public ResolvedSpeechSettings withTtsVoice(String voice) {
        return new ResolvedSpeechSettings(providerType, baseUrl, workspaceId, asrModel, asrMode,
                ttsModel, ttsMode, voice, apiKey);
    }
}
