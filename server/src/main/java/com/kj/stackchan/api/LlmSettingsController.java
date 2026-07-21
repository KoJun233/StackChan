package com.kj.stackchan.api;

import java.time.Instant;

import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/settings/llm", produces = MediaType.APPLICATION_JSON_VALUE)
public class LlmSettingsController {

    private final LlmSettingsService llmSettingsService;
    private final LlmRuntimeClientFactory llmRuntimeClientFactory;

    public LlmSettingsController(
            LlmSettingsService llmSettingsService,
            LlmRuntimeClientFactory llmRuntimeClientFactory
    ) {
        this.llmSettingsService = llmSettingsService;
        this.llmRuntimeClientFactory = llmRuntimeClientFactory;
    }

    @GetMapping
    public LlmSettingsResponse getSettings() {
        return toResponse(llmSettingsService.getSettings());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public LlmSettingsResponse saveSettings(@Valid @RequestBody UpdateLlmSettingsRequest request) {
        return toResponse(llmSettingsService.saveSettings(new LlmSettingsService.UpdateLlmSettingsCommand(
                request.baseUrl(),
                request.model(),
                request.systemPrompt(),
                request.apiKey()
        )));
    }

    @PostMapping(path = "/test")
    public LlmConnectionTestResponse testConnection() {
        String message = llmRuntimeClientFactory.createChatClient()
                .prompt()
                .user("请只返回 pong，不要解释。")
                .call()
                .content();
        return new LlmConnectionTestResponse(true, message);
    }

    private LlmSettingsResponse toResponse(LlmSettingsService.LlmSettingsSnapshot settings) {
        return new LlmSettingsResponse(
                settings.baseUrl(),
                settings.model(),
                settings.systemPrompt(),
                settings.apiKeyConfigured(),
                settings.updatedAt()
        );
    }

    public record UpdateLlmSettingsRequest(
            @NotBlank @Size(max = 2048) String baseUrl,
            @NotBlank @Size(max = 160) String model,
            @NotNull @Size(max = 12000) String systemPrompt,
            @Size(max = 4096) String apiKey
    ) {
    }

    public record LlmSettingsResponse(
            String baseUrl,
            String model,
            String systemPrompt,
            boolean apiKeyConfigured,
            Instant updatedAt
    ) {
    }

    public record LlmConnectionTestResponse(boolean ok, String message) {
    }
}
