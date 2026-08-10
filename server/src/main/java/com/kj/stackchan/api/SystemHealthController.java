package com.kj.stackchan.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.kj.stackchan.backup.BackupStatusService;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.expression.DeviceExpressionPackRepository;
import com.kj.stackchan.expression.DeviceExpressionPackStatus;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateJobEntity;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateJobRepository;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateStatus;
import com.kj.stackchan.health.ProviderHealthRegistry;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.speech.SpeechSettingsService;
import com.kj.stackchan.speech.VoiceTurnEntity;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import com.kj.stackchan.wakeword.WakeWordModelJobRepository;
import com.kj.stackchan.wakeword.WakeWordModelJobStatus;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/system/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class SystemHealthController {

    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(90);

    private final DeviceRepository deviceRepository;
    private final DeviceCommandGateway commandGateway;
    private final VoiceTurnRepository voiceTurnRepository;
    private final FirmwareUpdateJobRepository firmwareJobRepository;
    private final WakeWordModelJobRepository wakeJobRepository;
    private final DeviceExpressionPackRepository expressionRepository;
    private final ReminderRepository reminderRepository;
    private final BackupStatusService backupStatusService;
    private final LlmSettingsService llmSettingsService;
    private final SpeechSettingsService speechSettingsService;
    private final ProviderHealthRegistry providerHealthRegistry;
    private final ObjectProvider<Flyway> flywayProvider;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final Environment environment;
    private final Clock clock;

    public SystemHealthController(
            DeviceRepository deviceRepository,
            DeviceCommandGateway commandGateway,
            VoiceTurnRepository voiceTurnRepository,
            FirmwareUpdateJobRepository firmwareJobRepository,
            WakeWordModelJobRepository wakeJobRepository,
            DeviceExpressionPackRepository expressionRepository,
            ReminderRepository reminderRepository,
            BackupStatusService backupStatusService,
            LlmSettingsService llmSettingsService,
            SpeechSettingsService speechSettingsService,
            ProviderHealthRegistry providerHealthRegistry,
            ObjectProvider<Flyway> flywayProvider,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            Environment environment,
            Clock clock
    ) {
        this.deviceRepository = deviceRepository;
        this.commandGateway = commandGateway;
        this.voiceTurnRepository = voiceTurnRepository;
        this.firmwareJobRepository = firmwareJobRepository;
        this.wakeJobRepository = wakeJobRepository;
        this.expressionRepository = expressionRepository;
        this.reminderRepository = reminderRepository;
        this.backupStatusService = backupStatusService;
        this.llmSettingsService = llmSettingsService;
        this.speechSettingsService = speechSettingsService;
        this.providerHealthRegistry = providerHealthRegistry;
        this.flywayProvider = flywayProvider;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.environment = environment;
        this.clock = clock;
    }

    @GetMapping
    public SystemHealthResponse health() {
        boolean llmConfigured = llmSettingsService.getSettings().apiKeyConfigured();
        boolean speechConfigured = speechSettingsService.getSettings().apiKeyConfigured();
        return new SystemHealthResponse(
                serverVersion(),
                migrationVersion(),
                clock.instant(),
                devices(),
                List.of(
                        new ProviderStatus("llm", llmConfigured,
                                providerHealthRegistry.status("llm", llmConfigured)),
                        new ProviderStatus("speech", speechConfigured,
                                providerHealthRegistry.status("speech", speechConfigured))
                ),
                backupStatusService.status(),
                new PendingJobs(
                        firmwareJobRepository.countByStatusIn(EnumSet.of(
                                FirmwareUpdateStatus.READY, FirmwareUpdateStatus.INSTALLING)),
                        wakeJobRepository.countByStatusIn(EnumSet.of(
                                WakeWordModelJobStatus.READY, WakeWordModelJobStatus.INSTALLING)),
                        expressionRepository.countByStatusIn(EnumSet.of(
                                DeviceExpressionPackStatus.READY, DeviceExpressionPackStatus.INSTALLING)),
                        reminderRepository.countByStatusIn(EnumSet.of(
                                ReminderStatus.PENDING, ReminderStatus.DISPATCHED))
                ),
                recentSafeErrors()
        );
    }

    private String serverVersion() {
        String configuredVersion = environment.getProperty("companion.build-version");
        if (configuredVersion != null && !configuredVersion.isBlank()) {
            return configuredVersion.strip();
        }
        BuildProperties properties = buildPropertiesProvider.getIfAvailable();
        if (properties != null && properties.getVersion() != null) {
            return properties.getVersion();
        }
        return "development";
    }

    private String migrationVersion() {
        Flyway flyway = flywayProvider.getIfAvailable();
        MigrationInfo current = flyway == null ? null : flyway.info().current();
        return current == null || current.getVersion() == null ? "none" : current.getVersion().getVersion();
    }

    private List<DeviceHealth> devices() {
        Instant onlineAfter = clock.instant().minus(ONLINE_WINDOW);
        return deviceRepository.findAll().stream()
                .sorted(Comparator.comparing(DeviceEntity::getDisplayName).thenComparing(DeviceEntity::getId))
                .map(device -> new DeviceHealth(
                        device.getId(), device.getDisplayName(), device.getFirmwareVersion(), device.getRssi(),
                        device.getSafetyState(), device.getLastSeenAt(),
                        device.getLastSeenAt() != null && !device.getLastSeenAt().isBefore(onlineAfter),
                        commandGateway.isConnected(device.getId()), device.isApplicationOtaSupported()
                ))
                .toList();
    }

    private List<SafeError> recentSafeErrors() {
        Stream<SafeError> voiceErrors = voiceTurnRepository
                .findTop10ByStatusOrderByUpdatedAtDesc(VoiceTurnStatus.FAILED).stream()
                .map(this::voiceError);
        Stream<SafeError> firmwareErrors = firmwareJobRepository
                .findTop10ByStatusInOrderByUpdatedAtDesc(EnumSet.of(
                        FirmwareUpdateStatus.FAILED, FirmwareUpdateStatus.ROLLED_BACK)).stream()
                .map(this::firmwareError);
        return Stream.concat(voiceErrors, firmwareErrors)
                .sorted(Comparator.comparing(SafeError::occurredAt).reversed())
                .limit(10)
                .toList();
    }

    private SafeError voiceError(VoiceTurnEntity turn) {
        return new SafeError(
                "VOICE_TURN", turn.getDeviceId(), turn.getStatus().name(),
                turn.getFailureCode() == null ? "unknown" : turn.getFailureCode().name(), turn.getUpdatedAt()
        );
    }

    private SafeError firmwareError(FirmwareUpdateJobEntity job) {
        return new SafeError(
                "FIRMWARE_UPDATE", job.getDeviceId(), job.getStatus().name(),
                job.getFailureCode() == null ? "unknown" : job.getFailureCode(), job.getUpdatedAt()
        );
    }

    public record SystemHealthResponse(
            String serverVersion,
            String databaseMigration,
            Instant checkedAt,
            List<DeviceHealth> devices,
            List<ProviderStatus> providers,
            BackupStatusService.BackupStatus backup,
            PendingJobs pendingJobs,
            List<SafeError> recentSafeErrors
    ) {
    }

    public record DeviceHealth(
            UUID id,
            String displayName,
            String firmwareVersion,
            Integer rssi,
            String safetyState,
            Instant lastSeenAt,
            boolean online,
            boolean commandAvailable,
            boolean applicationOtaSupported
    ) {
    }

    public record ProviderStatus(
            String provider,
            boolean configured,
            ProviderHealthRegistry.ProviderConnectivity connectivity
    ) {
    }

    public record PendingJobs(
            long firmwareUpdates,
            long wakeModels,
            long expressionPacks,
            long reminders
    ) {
    }

    public record SafeError(
            String category,
            UUID deviceId,
            String status,
            String failureCode,
            Instant occurredAt
    ) {
    }
}
