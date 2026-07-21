package com.kj.stackchan.llm;

public record ResolvedLlmSettings(
        String baseUrl,
        String model,
        String systemPrompt,
        String apiKey
) {
}
