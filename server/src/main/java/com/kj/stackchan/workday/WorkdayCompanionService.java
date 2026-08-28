package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.kj.stackchan.calendar.ICloudCalendarService;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceEventService;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.reminder.ProactiveGenerationStatus;
import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import com.kj.stackchan.weather.WorkdayWeatherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkdayCompanionService {

    private static final Duration FIRST_BRIEF_WINDOW = Duration.ofHours(2);
    private static final Duration ACTIVE_VOICE_MAX_AGE = Duration.ofMinutes(15);
    private static final Duration NEAR_EVENT_WINDOW = Duration.ofMinutes(15);
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("M月d日H点mm分", Locale.CHINA);
    private static final String BRIEF_TOPIC = "workday:brief:";
    private static final String REST_TOPIC = "workday:rest:";

    private final WorkdayRuntimeService runtimeService;
    private final WorkdaySettingsService settingsService;
    private final InteractionSettingsService interactionSettingsService;
    private final ICloudCalendarService calendarService;
    private final WorkdayWeatherService weatherService;
    private final ReminderRepository reminderRepository;
    private final VoiceTurnRepository voiceTurnRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCommandGateway commandGateway;
    private final CompanionRoleService roleService;
    private final Clock clock;

    public WorkdayCompanionService(
            WorkdayRuntimeService runtimeService,
            WorkdaySettingsService settingsService,
            InteractionSettingsService interactionSettingsService,
            ICloudCalendarService calendarService,
            WorkdayWeatherService weatherService,
            ReminderRepository reminderRepository,
            VoiceTurnRepository voiceTurnRepository,
            DeviceRepository deviceRepository,
            DeviceCommandGateway commandGateway,
            CompanionRoleService roleService,
            Clock clock
    ) {
        this.runtimeService = runtimeService;
        this.settingsService = settingsService;
        this.interactionSettingsService = interactionSettingsService;
        this.calendarService = calendarService;
        this.weatherService = weatherService;
        this.reminderRepository = reminderRepository;
        this.voiceTurnRepository = voiceTurnRepository;
        this.deviceRepository = deviceRepository;
        this.commandGateway = commandGateway;
        this.roleService = roleService;
        this.clock = clock;
    }

    public WorkdayRuntimeService.WorkdayRuntimeSnapshot start(UUID deviceId) {
        if (!commandGateway.isConnected(deviceId)) {
            throw new InvalidWorkdayStateException("Device is offline");
        }
        Instant now = clock.instant();
        InteractionSettingsService.InteractionSettingsSnapshot interaction = interactionSettingsService.resolve(deviceId);
        if (interactionSettingsService.isDnd(interaction, now)) {
            throw new InvalidWorkdayStateException("Workday mode cannot start during do-not-disturb");
        }
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new InvalidWorkdayStateException("Workday device is invalid"));
        boolean present = !device.getBodyDiagnostics().proximitySupported()
                || device.getBodyDiagnostics().present();
        WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime = runtimeService.start(deviceId, present);
        CompanionRoleService.RoleSnapshot role = roleService.getActive(deviceId);
        commandGateway.configureExpression(deviceId, role.expressionThemeColor(), "JOY", "WEAK", 5);
        playIfArmed(device, "WAKE");
        processActiveDevice(deviceId);
        return runtimeService.get(deviceId);
    }

    public WorkdayRuntimeService.WorkdayRuntimeSnapshot stop(UUID deviceId) {
        WorkdayRuntimeService.WorkdayRuntimeSnapshot stopped = runtimeService.stop(deviceId);
        cancelPending(deviceId, BRIEF_TOPIC);
        cancelPending(deviceId, REST_TOPIC);
        return stopped;
    }

    public WorkdayRuntimeService.WorkdayRuntimeSnapshot toggleFromDevice(UUID deviceId) {
        return runtimeService.get(deviceId).state() == WorkdayRuntimeState.OFF
                ? start(deviceId) : stop(deviceId);
    }

    public WorkdayRuntimeService.WorkdayRuntimeSnapshot respondToRest(UUID deviceId, WorkdayRestAction action) {
        WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime = runtimeService.respondToRest(deviceId, action);
        cancelPending(deviceId, REST_TOPIC);
        return runtime;
    }

    public void presenceChanged(UUID deviceId, boolean present) {
        WorkdayRuntimeService.WorkdayRuntimeSnapshot current = runtimeService.get(deviceId);
        if (current.state() == WorkdayRuntimeState.OFF) {
            return;
        }
        WorkdayRuntimeService.PresenceUpdateSnapshot update = runtimeService.updatePresenceWithOutcome(deviceId, present);
        if (update.rearrival()) {
            DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
            CompanionRoleService.RoleSnapshot role = roleService.getActive(deviceId);
            commandGateway.configureExpression(deviceId, role.expressionThemeColor(), "JOY", "WEAK", 4);
            playIfArmed(device, "LOOK_USER");
        }
    }

    @Transactional
    public void processActiveDevice(UUID deviceId) {
        WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime;
        try {
            runtime = runtimeService.tick(deviceId);
        } catch (InvalidWorkdayStateException exception) {
            return;
        }
        reconcileBrief(runtime);
        if (runtime.state() == WorkdayRuntimeState.OFF) {
            cancelPending(deviceId, BRIEF_TOPIC);
            cancelPending(deviceId, REST_TOPIC);
            return;
        }
        if (!commandGateway.isConnected(deviceId)) {
            return;
        }
        if (runtime.present() && (runtime.briefStatus() == null
                || runtime.briefStatus() == WorkdayBriefStatus.PENDING)) {
            queueBrief(runtime);
        }
        if (runtime.state() == WorkdayRuntimeState.REST_PROMPTED && canPromptRest(deviceId)) {
            queueRestPrompt(runtime);
        }
    }

    private void queueBrief(WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime) {
        if (runtime.workDate() == null || runtime.startedAt() == null) return;
        Instant now = clock.instant();
        if (now.isAfter(runtime.startedAt().plus(FIRST_BRIEF_WINDOW))) {
            if (runtime.briefStatus() == WorkdayBriefStatus.PENDING) {
                runtimeService.completeBrief(runtime.deviceId(), runtime.workDate(), WorkdayBriefStatus.CANCELLED);
            }
            return;
        }
        InteractionSettingsService.InteractionSettingsSnapshot interaction =
                interactionSettingsService.resolve(runtime.deviceId());
        if (interactionSettingsService.isDnd(interaction, now) || isAudioBusy(runtime.deviceId())) return;
        String prefix = briefPrefix(runtime.workDate());
        if (reminderRepository.findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                runtime.deviceId(), ReminderSource.PROACTIVE, prefix).isPresent()) return;

        WorkdayRuntimeService.BriefClaimSnapshot claim = runtimeService.claimBrief(runtime.deviceId());
        if (claim.status() != WorkdayBriefStatus.PENDING) return;
        BriefContent brief = brief(runtime.deviceId());
        CompanionRoleService.RoleSnapshot role = roleService.getActive(runtime.deviceId());
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(runtime.deviceId());
        reminderRepository.save(new ReminderEntity(
                role.id(), runtime.deviceId(), brief.text(), now, settings.zoneId(),
                ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE,
                prefix + brief.status().name(), ProactiveGenerationStatus.FIXED, now
        ));
    }

    private BriefContent brief(UUID deviceId) {
        Instant now = clock.instant();
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        ZoneId zone = ZoneId.of(settings.zoneId());
        CompanionRoleService.RoleSnapshot role = roleService.getActive(deviceId);
        StringBuilder text = new StringBuilder("主人，").append(role.name()).append("陪您开始今天的工作。");
        boolean weatherAvailable = false;
        boolean calendarAvailable = false;
        try {
            WorkdayWeatherService.WeatherSnapshot weather = weatherService.get(deviceId);
            if (weather.configured() && weather.fresh() && weather.summary() != null) {
                text.append(weather.summary()).append('。');
                weatherAvailable = true;
            }
        } catch (RuntimeException ignored) {
            // Each factual segment degrades independently.
        }
        try {
            ICloudCalendarService.ConnectionSnapshot connection = calendarService.get(deviceId);
            calendarAvailable = connection.configured() && connection.lastSyncedAt() != null
                    && connection.lastSyncedAt().plus(Duration.ofHours(24)).isAfter(now);
            if (calendarAvailable) {
                List<ICloudCalendarService.CachedEventSnapshot> events = calendarService
                        .cachedEvents(deviceId, now, now.plus(Duration.ofDays(7))).stream().limit(2).toList();
                if (events.isEmpty()) {
                    text.append("未来七天没有已安排的日程。");
                } else {
                    text.append("接下来");
                    for (int index = 0; index < events.size(); index++) {
                        ICloudCalendarService.CachedEventSnapshot event = events.get(index);
                        if (index > 0) text.append("；");
                        text.append(event.startsAt().atZone(zone).format(EVENT_TIME))
                                .append(' ').append(event.privateEvent() ? "私人日程" : event.title());
                    }
                    text.append('。');
                }
            }
        } catch (RuntimeException ignored) {
            // Each factual segment degrades independently.
        }
        if (!weatherAvailable && !calendarAvailable) {
            text.append("天气和日历信息暂不可用，本次先跳过。");
        } else if (!weatherAvailable) {
            text.append("天气信息暂不可用，本次先跳过。");
        } else if (!calendarAvailable) {
            text.append("日历信息暂不可用，本次先跳过。");
        }
        WorkdayBriefStatus status = weatherAvailable && calendarAvailable
                ? WorkdayBriefStatus.SUCCESS : WorkdayBriefStatus.PARTIAL;
        return new BriefContent(text.toString(), status);
    }

    private void reconcileBrief(WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime) {
        if (runtime.workDate() == null || runtime.briefStatus() != WorkdayBriefStatus.PENDING) return;
        reminderRepository.findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                        runtime.deviceId(), ReminderSource.PROACTIVE, briefPrefix(runtime.workDate()))
                .ifPresent(reminder -> {
                    WorkdayBriefStatus completed = switch (reminder.getStatus()) {
                        case DELIVERED -> reminder.getProactiveTopicKey().endsWith(WorkdayBriefStatus.SUCCESS.name())
                                ? WorkdayBriefStatus.SUCCESS : WorkdayBriefStatus.PARTIAL;
                        case FAILED -> WorkdayBriefStatus.FAILED;
                        case CANCELLED, EXPIRED, SKIPPED -> WorkdayBriefStatus.CANCELLED;
                        default -> null;
                    };
                    if (completed != null) {
                        runtimeService.completeBrief(runtime.deviceId(), runtime.workDate(), completed);
                    }
                });
    }

    private boolean canPromptRest(UUID deviceId) {
        Instant now = clock.instant();
        InteractionSettingsService.InteractionSettingsSnapshot interaction = interactionSettingsService.resolve(deviceId);
        if (interactionSettingsService.isDnd(interaction, now) || isAudioBusy(deviceId)) return false;
        try {
            return calendarService.cachedEvents(deviceId, now, now.plus(NEAR_EVENT_WINDOW)).isEmpty();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private boolean isAudioBusy(UUID deviceId) {
        return reminderRepository.existsByDeviceIdAndStatus(deviceId, ReminderStatus.DISPATCHED)
                || voiceTurnRepository.existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                deviceId,
                List.of(VoiceTurnStatus.IN_PROGRESS, VoiceTurnStatus.RESPONSE_READY),
                clock.instant().minus(ACTIVE_VOICE_MAX_AGE)
        );
    }

    private void queueRestPrompt(WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime) {
        if (runtime.workDate() == null || runtime.stateChangedAt() == null) return;
        String topic = REST_TOPIC + runtime.workDate() + ":" + runtime.stateChangedAt().getEpochSecond();
        if (reminderRepository.findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                runtime.deviceId(), ReminderSource.PROACTIVE, topic).isPresent()) return;
        CompanionRoleService.RoleSnapshot role = roleService.getActive(runtime.deviceId());
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(runtime.deviceId());
        Instant now = clock.instant();
        String content = "主人，已经专注 " + settings.focusMinutes()
                + " 分钟。要开始休息、稍后十分钟，还是今天跳过休息提醒？";
        reminderRepository.save(new ReminderEntity(
                role.id(), runtime.deviceId(), content, now, settings.zoneId(),
                ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE,
                topic, ProactiveGenerationStatus.FIXED, now
        ));
    }

    private void cancelPending(UUID deviceId, String topicPrefix) {
        Instant now = clock.instant();
        for (ReminderEntity reminder : reminderRepository
                .findAllByDeviceIdAndSourceAndProactiveTopicKeyStartingWith(
                        deviceId, ReminderSource.PROACTIVE, topicPrefix)) {
            if (reminder.getStatus() == ReminderStatus.PENDING) {
                reminder.markCancelled(now);
                reminderRepository.save(reminder);
            }
        }
    }

    private void playIfArmed(DeviceEntity device, String motion) {
        if (device != null && DeviceEventService.MOTION_ARMED.equals(device.getSafetyState())) {
            commandGateway.playBodyMotion(device.getId(), motion);
        }
    }

    private String briefPrefix(LocalDate workDate) {
        return BRIEF_TOPIC + workDate + ":";
    }

    private record BriefContent(String text, WorkdayBriefStatus status) { }
}
