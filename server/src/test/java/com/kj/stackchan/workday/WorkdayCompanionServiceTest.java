package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.calendar.ICloudCalendarConnectionStatus;
import com.kj.stackchan.calendar.ICloudCalendarService;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceEventService;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.reminder.ProactiveGenerationStatus;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.weather.WorkdayWeatherService;
import com.kj.stackchan.weather.WorkdayWeatherStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayCompanionServiceTest {

    private static final UUID DEVICE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-29T01:00:00Z");

    @Mock private WorkdayRuntimeService runtimeService;
    @Mock private WorkdaySettingsService settingsService;
    @Mock private InteractionSettingsService interactionSettingsService;
    @Mock private ICloudCalendarService calendarService;
    @Mock private WorkdayWeatherService weatherService;
    @Mock private ReminderRepository reminderRepository;
    @Mock private VoiceTurnRepository voiceTurnRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private DeviceCommandGateway commandGateway;
    @Mock private CompanionRoleService roleService;
    @Mock private WorkdayPilotService pilotService;
    @Mock private InteractionSettingsService.InteractionSettingsSnapshot interaction;
    @Mock private CompanionRoleService.RoleSnapshot role;

    private WorkdayCompanionService service;

    @BeforeEach
    void setUp() {
        service = new WorkdayCompanionService(
                runtimeService, settingsService, interactionSettingsService, calendarService,
                weatherService, reminderRepository, voiceTurnRepository, deviceRepository,
                commandGateway, roleService, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        service.setPilotService(pilotService);
        lenient().when(commandGateway.isConnected(DEVICE_ID)).thenReturn(true);
        lenient().when(interactionSettingsService.resolve(DEVICE_ID)).thenReturn(interaction);
        lenient().when(interactionSettingsService.isDnd(interaction, NOW)).thenReturn(false);
        lenient().when(reminderRepository.existsByDeviceIdAndStatus(DEVICE_ID, ReminderStatus.DISPATCHED)).thenReturn(false);
        lenient().when(roleService.getActive(DEVICE_ID)).thenReturn(role);
        lenient().when(role.id()).thenReturn(ROLE_ID);
        lenient().when(role.name()).thenReturn("小峰");
        lenient().when(settingsService.resolve(DEVICE_ID)).thenReturn(settings());
    }

    @Test
    void queuesOneDeterministicBriefAndMasksPrivateEvent() {
        LocalDate date = LocalDate.of(2026, 8, 29);
        when(runtimeService.tick(DEVICE_ID)).thenReturn(runtime(
                WorkdayRuntimeState.ACTIVE_PRESENT, date, null, NOW.minusSeconds(60)
        ));
        when(reminderRepository.findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                eq(DEVICE_ID), eq(ReminderSource.PROACTIVE), any())).thenReturn(Optional.empty());
        when(runtimeService.claimBrief(DEVICE_ID)).thenReturn(new WorkdayRuntimeService.BriefClaimSnapshot(
                UUID.randomUUID(), DEVICE_ID, date, WorkdayBriefStatus.PENDING, true, NOW, null, NOW
        ));
        when(weatherService.get(DEVICE_ID)).thenReturn(new WorkdayWeatherService.WeatherSnapshot(
                DEVICE_ID, true, true, "上海", "Asia/Shanghai", WorkdayWeatherStatus.READY,
                null, NOW, NOW, NOW.plusSeconds(3600), "上海今天有阵雨，建议带伞", null, List.of()
        ));
        when(calendarService.get(DEVICE_ID)).thenReturn(new ICloudCalendarService.ConnectionSnapshot(
                DEVICE_ID, true, "a***@example.com", true, ICloudCalendarConnectionStatus.CONNECTED,
                null, NOW, NOW.minusSeconds(60), 1, NOW.plusSeconds(3600), List.of()
        ));
        when(calendarService.cachedEvents(eq(DEVICE_ID), eq(NOW), any())).thenReturn(List.of(
                new ICloudCalendarService.CachedEventSnapshot(
                        NOW.plusSeconds(3600), NOW.plusSeconds(5400), false, true, true,
                        "不应进入语音的秘密标题", ""
                )
        ));

        service.processActiveDevice(DEVICE_ID);

        ArgumentCaptor<ReminderEntity> reminder = ArgumentCaptor.forClass(ReminderEntity.class);
        verify(reminderRepository).save(reminder.capture());
        assertThat(reminder.getValue().getContent())
                .contains("建议带伞", "私人日程")
                .doesNotContain("秘密标题");
        assertThat(reminder.getValue().getProactiveTopicKey()).endsWith(WorkdayBriefStatus.SUCCESS.name());
    }

    @Test
    void queuesRestPromptOnlyAfterSuppressionChecksPass() {
        LocalDate date = LocalDate.of(2026, 8, 29);
        when(runtimeService.tick(DEVICE_ID)).thenReturn(runtime(
                WorkdayRuntimeState.REST_PROMPTED, date, WorkdayBriefStatus.SUCCESS, NOW.minusSeconds(3600)
        ));
        when(calendarService.cachedEvents(eq(DEVICE_ID), eq(NOW), any())).thenReturn(List.of());
        when(reminderRepository.findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                eq(DEVICE_ID), eq(ReminderSource.PROACTIVE), any())).thenReturn(Optional.empty());

        service.processActiveDevice(DEVICE_ID);

        ArgumentCaptor<ReminderEntity> reminder = ArgumentCaptor.forClass(ReminderEntity.class);
        verify(reminderRepository).save(reminder.capture());
        assertThat(reminder.getValue().getContent()).contains("专注 50 分钟", "稍后十分钟", "今天跳过");
        assertThat(reminder.getValue().getProactiveTopicKey()).startsWith("workday:rest:2026-08-29:");
    }

    @Test
    void rearrivalNeverMovesADeviceThatIsNotAlreadyArmed() {
        DeviceEntity device = org.mockito.Mockito.mock(DeviceEntity.class);
        when(runtimeService.get(DEVICE_ID)).thenReturn(runtime(
                WorkdayRuntimeState.ACTIVE_ABSENT, LocalDate.of(2026, 8, 29),
                WorkdayBriefStatus.SUCCESS, NOW.minusSeconds(3600)
        ));
        when(runtimeService.updatePresenceWithOutcome(DEVICE_ID, true)).thenReturn(
                new WorkdayRuntimeService.PresenceUpdateSnapshot(
                        runtime(WorkdayRuntimeState.ACTIVE_PRESENT, LocalDate.of(2026, 8, 29),
                                WorkdayBriefStatus.SUCCESS, NOW.minusSeconds(3600)), true
                )
        );
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(device.getSafetyState()).thenReturn(DeviceEventService.MOTION_DISABLED);

        service.presenceChanged(DEVICE_ID, true);

        verify(commandGateway, never()).playBodyMotion(any(), any());
    }

    @Test
    void stoppingPersistsCancellationOfPendingWorkdayDelivery() {
        ReminderEntity pending = new ReminderEntity(
                ROLE_ID, DEVICE_ID, "工作简报", NOW, "Asia/Shanghai",
                ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE,
                "workday:brief:2026-08-29:SUCCESS", ProactiveGenerationStatus.FIXED, NOW
        );
        when(runtimeService.stop(DEVICE_ID)).thenReturn(runtime(
                WorkdayRuntimeState.OFF, LocalDate.of(2026, 8, 29),
                WorkdayBriefStatus.CANCELLED, NOW.minusSeconds(60)
        ));
        when(reminderRepository.findAllByDeviceIdAndSourceAndProactiveTopicKeyStartingWith(
                eq(DEVICE_ID), eq(ReminderSource.PROACTIVE), any())).thenReturn(List.of(pending));

        service.stop(DEVICE_ID);

        assertThat(pending.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        verify(reminderRepository).save(pending);
    }

    @Test
    void attributesStaleExternalCachesWhenBuildingTheDailyBrief() {
        LocalDate date = LocalDate.of(2026, 8, 29);
        when(runtimeService.tick(DEVICE_ID)).thenReturn(runtime(
                WorkdayRuntimeState.ACTIVE_PRESENT, date, null, NOW.minusSeconds(60)
        ));
        when(reminderRepository.findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                eq(DEVICE_ID), eq(ReminderSource.PROACTIVE), any())).thenReturn(Optional.empty());
        when(runtimeService.claimBrief(DEVICE_ID)).thenReturn(new WorkdayRuntimeService.BriefClaimSnapshot(
                UUID.randomUUID(), DEVICE_ID, date, WorkdayBriefStatus.PENDING, true, NOW, null, NOW
        ));
        when(weatherService.get(DEVICE_ID)).thenReturn(new WorkdayWeatherService.WeatherSnapshot(
                DEVICE_ID, true, false, "上海", "Asia/Shanghai", WorkdayWeatherStatus.READY,
                null, NOW.minusSeconds(7200), NOW.minusSeconds(7200), NOW.minusSeconds(3600),
                null, null, List.of()
        ));
        when(calendarService.get(DEVICE_ID)).thenReturn(new ICloudCalendarService.ConnectionSnapshot(
                DEVICE_ID, true, "a***@example.com", true, ICloudCalendarConnectionStatus.CONNECTED,
                null, NOW, NOW.minusSeconds(25 * 3600), 1, NOW.minusSeconds(3600), List.of()
        ));

        service.processActiveDevice(DEVICE_ID);

        verify(pilotService).recordBriefDegradation(
                DEVICE_ID, date, "STALE_CACHE", "STALE_CACHE"
        );
    }

    private WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime(
            WorkdayRuntimeState state,
            LocalDate date,
            WorkdayBriefStatus briefStatus,
            Instant startedAt
    ) {
        return new WorkdayRuntimeService.WorkdayRuntimeSnapshot(
                DEVICE_ID, state, date, true, 0, 3000, startedAt, NOW,
                null, null, null, briefStatus, NOW
        );
    }

    private WorkdaySettingsService.WorkdaySettingsSnapshot settings() {
        return new WorkdaySettingsService.WorkdaySettingsSnapshot(
                DEVICE_ID, true, 127, LocalTime.MIN, LocalTime.of(23, 59),
                50, 10, 10, 45, "上海", 31.2, 121.5, "Asia/Shanghai", NOW
        );
    }
}
