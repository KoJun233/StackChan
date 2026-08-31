package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.role.CompanionRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayPilotCompletionServiceTest {

    private static final UUID DEVICE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-13T00:05:00Z");
    private static final LocalDate STARTED_ON = LocalDate.of(2026, 8, 30);

    @Mock private WorkdayPilotObservationRepository observationRepository;
    @Mock private WorkdayPilotService pilotService;
    @Mock private ReminderRepository reminderRepository;
    @Mock private CompanionRoleService roleService;
    @Mock private CompanionRoleService.RoleSnapshot role;
    @Mock private WorkdayPilotService.PilotReportSnapshot report;

    private WorkdayPilotCompletionService service;

    @BeforeEach
    void setUp() {
        service = new WorkdayPilotCompletionService(
                observationRepository, pilotService, reminderRepository, roleService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listsOnlyUnqueuedObservationCandidates() {
        WorkdayPilotObservationEntity observation = observation();
        when(observationRepository.findAllByCompletionNotificationQueuedAtIsNullOrderByEndsOnAsc())
                .thenReturn(List.of(observation));

        assertThat(service.candidateDeviceIds()).containsExactly(DEVICE_ID);
    }

    @Test
    void doesNotQueueBeforeTheFinalDayHasFullyEnded() {
        WorkdayPilotObservationEntity observation = observation();
        when(observationRepository.findForUpdate(DEVICE_ID)).thenReturn(Optional.of(observation));
        when(pilotService.get(DEVICE_ID)).thenReturn(report);

        assertThat(service.queueIfComplete(DEVICE_ID)).isFalse();
        assertThat(observation.getCompletionNotificationQueuedAt()).isNull();
        verify(reminderRepository, never()).save(any());
        verify(roleService, never()).getActive(any());
    }

    @Test
    void queuesOneDeterministicPassNotificationAndMarksObservation() {
        WorkdayPilotObservationEntity observation = observation();
        when(observationRepository.findForUpdate(DEVICE_ID)).thenReturn(Optional.of(observation));
        when(pilotService.get(DEVICE_ID)).thenReturn(report);
        when(report.windowComplete()).thenReturn(true);
        when(report.status()).thenReturn(WorkdayPilotService.PilotStatus.PASS);
        when(reminderRepository
                .findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                        DEVICE_ID, ReminderSource.PROACTIVE,
                        "workday:pilot:complete:2026-08-30:PASS"
                )).thenReturn(Optional.empty());
        when(roleService.getActive(DEVICE_ID)).thenReturn(role);
        when(role.id()).thenReturn(ROLE_ID);

        assertThat(service.queueIfComplete(DEVICE_ID)).isTrue();

        ArgumentCaptor<ReminderEntity> reminder = ArgumentCaptor.forClass(ReminderEntity.class);
        verify(reminderRepository).save(reminder.capture());
        assertThat(reminder.getValue().getRoleId()).isEqualTo(ROLE_ID);
        assertThat(reminder.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(reminder.getValue().getContent()).contains("门槛已通过");
        assertThat(reminder.getValue().getContent()).contains("管理页面");
        assertThat(reminder.getValue().getProactiveTopicKey())
                .isEqualTo("workday:pilot:complete:2026-08-30:PASS");
        assertThat(observation.getCompletionNotificationQueuedAt()).isEqualTo(NOW);
    }

    @Test
    void reconcilesAnExistingNotificationWithoutQueuingAnother() {
        WorkdayPilotObservationEntity observation = observation();
        ReminderEntity existing = org.mockito.Mockito.mock(ReminderEntity.class);
        when(observationRepository.findForUpdate(DEVICE_ID)).thenReturn(Optional.of(observation));
        when(pilotService.get(DEVICE_ID)).thenReturn(report);
        when(report.windowComplete()).thenReturn(true);
        when(report.status()).thenReturn(WorkdayPilotService.PilotStatus.FAIL);
        when(reminderRepository
                .findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
                        DEVICE_ID, ReminderSource.PROACTIVE,
                        "workday:pilot:complete:2026-08-30:FAIL"
                )).thenReturn(Optional.of(existing));

        assertThat(service.queueIfComplete(DEVICE_ID)).isFalse();
        assertThat(observation.getCompletionNotificationQueuedAt()).isEqualTo(NOW);
        verify(reminderRepository, never()).save(any());
        verify(roleService, never()).getActive(any());
    }

    private WorkdayPilotObservationEntity observation() {
        return new WorkdayPilotObservationEntity(
                DEVICE_ID, STARTED_ON, "Asia/Shanghai", 31,
                Instant.parse("2026-08-30T09:35:33Z")
        );
    }
}
