package com.kj.stackchan.api;

import java.time.LocalTime;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceInteractionSettingsCoordinator;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.MissedReminderPolicy;
import com.kj.stackchan.interaction.ProactiveTopicCooldownService;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/settings/interactions", produces = MediaType.APPLICATION_JSON_VALUE)
public class InteractionSettingsController {

    private final InteractionSettingsService settingsService;
    private final DeviceInteractionSettingsCoordinator settingsCoordinator;
    private final DeviceCommandGateway commandGateway;
    private final VoiceTurnDiagnosticsService voiceTurnDiagnosticsService;
    private final ProactiveTopicCooldownService topicCooldownService;

    public InteractionSettingsController(
            InteractionSettingsService settingsService,
            DeviceInteractionSettingsCoordinator settingsCoordinator,
            DeviceCommandGateway commandGateway,
            VoiceTurnDiagnosticsService voiceTurnDiagnosticsService,
            ProactiveTopicCooldownService topicCooldownService
    ) {
        this.settingsService = settingsService;
        this.settingsCoordinator = settingsCoordinator;
        this.commandGateway = commandGateway;
        this.voiceTurnDiagnosticsService = voiceTurnDiagnosticsService;
        this.topicCooldownService = topicCooldownService;
    }

    @GetMapping("/{deviceId}")
    public InteractionSettingsService.InteractionSettingsSnapshot get(@PathVariable UUID deviceId) {
        return settingsService.get(deviceId);
    }

    @PutMapping(path = "/{deviceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InteractionSettingsService.InteractionSettingsSnapshot save(
            @PathVariable UUID deviceId,
            @Valid @RequestBody InteractionSettingsRequest request
    ) {
        var settings = settingsService.save(deviceId, request.toCommand());
        settingsCoordinator.send(settings);
        return settings;
    }

    @PostMapping("/{deviceId}:stop")
    public CommandResponse stop(@PathVariable UUID deviceId) {
        boolean accepted = commandGateway.stopAudio(deviceId);
        if (accepted) {
            voiceTurnDiagnosticsService.cancelActiveTurns(deviceId);
        }
        return new CommandResponse(accepted);
    }

    @GetMapping("/{deviceId}/proactive-topics")
    public java.util.List<ProactiveTopicCooldownService.TopicCooldownSnapshot> topics(@PathVariable UUID deviceId) {
        return topicCooldownService.list(deviceId);
    }

    @PostMapping(path = "/{deviceId}/proactive-topics:resume", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProactiveTopicCooldownService.TopicCooldownSnapshot resumeTopic(
            @PathVariable UUID deviceId,
            @Valid @RequestBody ResumeTopicRequest request
    ) {
        return topicCooldownService.resume(deviceId, request.topicKey());
    }

    public record InteractionSettingsRequest(
            @Min(0) @Max(100) int volumePercent,
            boolean nightMode,
            boolean continuousConversationEnabled,
            @Min(3) @Max(8) int followUpWindowSeconds,
            boolean dndEnabled,
            @NotNull LocalTime dndStart,
            @NotNull LocalTime dndEnd,
            @NotBlank @Size(max = 80) String zoneId,
            @NotNull MissedReminderPolicy missedReminderPolicy,
            @Min(1) @Max(1440) int missedSnoozeMinutes,
            boolean proactiveEnabled,
            @NotNull LocalTime proactiveStart,
            @NotNull LocalTime proactiveEnd,
            @Min(30) @Max(1440) int proactiveMinIntervalMinutes,
            @Min(1) @Max(10) int proactiveDailyLimit,
            @NotBlank @Size(max = 500) String proactiveContent,
            boolean proactivePersonalizationEnabled
    ) {
        InteractionSettingsService.UpdateInteractionSettingsCommand toCommand() {
            return new InteractionSettingsService.UpdateInteractionSettingsCommand(
                    volumePercent, nightMode, continuousConversationEnabled, followUpWindowSeconds,
                    dndEnabled, dndStart, dndEnd, zoneId,
                    missedReminderPolicy, missedSnoozeMinutes, proactiveEnabled, proactiveStart,
                    proactiveEnd, proactiveMinIntervalMinutes, proactiveDailyLimit, proactiveContent,
                    proactivePersonalizationEnabled
            );
        }

        public InteractionSettingsRequest(
                int volumePercent, boolean nightMode, boolean continuousConversationEnabled,
                int followUpWindowSeconds, boolean dndEnabled, LocalTime dndStart, LocalTime dndEnd,
                String zoneId, MissedReminderPolicy missedReminderPolicy, int missedSnoozeMinutes,
                boolean proactiveEnabled, LocalTime proactiveStart, LocalTime proactiveEnd,
                int proactiveMinIntervalMinutes, int proactiveDailyLimit, String proactiveContent
        ) {
            this(volumePercent, nightMode, continuousConversationEnabled, followUpWindowSeconds,
                    dndEnabled, dndStart, dndEnd, zoneId, missedReminderPolicy, missedSnoozeMinutes,
                    proactiveEnabled, proactiveStart, proactiveEnd, proactiveMinIntervalMinutes,
                    proactiveDailyLimit, proactiveContent, false);
        }
    }

    public record ResumeTopicRequest(@NotBlank @Size(max = 120) String topicKey) {
    }

    public record CommandResponse(boolean accepted) {
    }
}
