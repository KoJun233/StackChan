package com.kj.stackchan.api;

import java.time.Instant;

import com.kj.stackchan.device.DeviceVoiceSettingsCoordinator;
import com.kj.stackchan.health.ProviderHealthRegistry;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import com.kj.stackchan.speech.SpeechAccessMode;
import com.kj.stackchan.speech.SpeechProviderType;
import com.kj.stackchan.speech.SpeechSettingsService;
import com.kj.stackchan.speech.VoiceWakeSensitivity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping(path = "/api/v1/settings/speech", produces = MediaType.APPLICATION_JSON_VALUE)
public class SpeechSettingsController {

    private final SpeechSettingsService settingsService;
    private final SpeechRuntimeClient speechRuntimeClient;
    private final DeviceVoiceSettingsCoordinator deviceVoiceSettingsCoordinator;
    private final ProviderHealthRegistry providerHealthRegistry;

    public SpeechSettingsController(
            SpeechSettingsService settingsService,
            SpeechRuntimeClient speechRuntimeClient,
            DeviceVoiceSettingsCoordinator deviceVoiceSettingsCoordinator,
            ProviderHealthRegistry providerHealthRegistry
    ) {
        this.settingsService = settingsService;
        this.speechRuntimeClient = speechRuntimeClient;
        this.deviceVoiceSettingsCoordinator = deviceVoiceSettingsCoordinator;
        this.providerHealthRegistry = providerHealthRegistry;
    }

    @GetMapping
    public SpeechSettingsResponse getSettings() {
        return toResponse(settingsService.getSettings());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public SpeechSettingsResponse saveSettings(@Valid @RequestBody UpdateSpeechSettingsRequest request) {
        SpeechSettingsService.SpeechSettingsSnapshot settings = settingsService.saveSettings(
                new SpeechSettingsService.UpdateSpeechSettingsCommand(
                        request.providerType(),
                        request.baseUrl(),
                        request.workspaceId(),
                        request.asrModel(),
                        request.asrMode(),
                        request.ttsModel(),
                        request.ttsMode(),
                        request.ttsVoice(),
                        request.wakeSensitivity(),
                        request.speechStartThreshold(),
                        request.speechSilenceThreshold(),
                        request.apiKey()
                ));
        deviceVoiceSettingsCoordinator.broadcast(settings);
        return toResponse(settings);
    }

    @PostMapping("/test")
    public SpeechConnectionTestResponse testConnection() {
        try {
            speechRuntimeClient.testConnection();
            providerHealthRegistry.succeeded("speech");
            return new SpeechConnectionTestResponse(true, "测试音频已成功生成并识别。");
        } catch (RuntimeException exception) {
            providerHealthRegistry.failed("speech");
            throw exception;
        }
    }

    private SpeechSettingsResponse toResponse(SpeechSettingsService.SpeechSettingsSnapshot settings) {
        return new SpeechSettingsResponse(
                settings.providerType(),
                settings.baseUrl(),
                settings.workspaceId(),
                settings.asrModel(),
                settings.asrMode(),
                settings.ttsModel(),
                settings.ttsMode(),
                settings.ttsVoice(),
                settings.wakeSensitivity(),
                settings.speechStartThreshold(),
                settings.speechSilenceThreshold(),
                settings.apiKeyConfigured(),
                settings.updatedAt()
        );
    }

    public record UpdateSpeechSettingsRequest(
            SpeechProviderType providerType,
            @Size(max = 2048) String baseUrl,
            @Size(max = 160) String workspaceId,
            @NotBlank @Size(max = 160) String asrModel,
            @NotNull SpeechAccessMode asrMode,
            @NotBlank @Size(max = 160) String ttsModel,
            @NotNull SpeechAccessMode ttsMode,
            @NotBlank @Size(max = 160) String ttsVoice,
            @NotNull VoiceWakeSensitivity wakeSensitivity,
            @Min(100) @Max(5000) int speechStartThreshold,
            @Min(50) @Max(4000) int speechSilenceThreshold,
            @Size(max = 4096) String apiKey
    ) {
    }

    public record SpeechSettingsResponse(
            SpeechProviderType providerType,
            String baseUrl,
            String workspaceId,
            String asrModel,
            SpeechAccessMode asrMode,
            String ttsModel,
            SpeechAccessMode ttsMode,
            String ttsVoice,
            VoiceWakeSensitivity wakeSensitivity,
            int speechStartThreshold,
            int speechSilenceThreshold,
            boolean apiKeyConfigured,
            Instant updatedAt
    ) {
    }

    public record SpeechConnectionTestResponse(boolean ok, String message) {
    }
}
