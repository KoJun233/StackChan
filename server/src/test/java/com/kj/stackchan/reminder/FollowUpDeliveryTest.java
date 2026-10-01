package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.interaction.*;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FollowUpDeliveryTest {
    private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");
    private final ReminderRepository repository = mock(ReminderRepository.class);
    private final DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
    private final SpeechRuntimeClient speech = mock(SpeechRuntimeClient.class);
    private final ReminderEntity followUp = new ReminderEntity(UUID.randomUUID(), UUID.randomUUID(),
            "面试结果怎么样？", NOW, "UTC", ReminderRecurrence.NONE, 1, null,
            ReminderSource.FOLLOW_UP, "follow-up:test", ProactiveGenerationStatus.FIXED, NOW);
    private ReminderDeliveryService service;

    @BeforeEach
    void setup() {
        followUp.assignFollowUpExpiry(NOW.plusSeconds(14400));
        service = new ReminderDeliveryService(repository, gateway, speech, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(ReminderStatus.PENDING, NOW))
                .thenReturn(List.of(followUp));
    }

    @Test
    void quietAndPartnerPauseCancelCareWithoutSendingAudio() {
        var quiet = mock(DeviceQuietTodayService.class);
        when(quiet.isQuiet(followUp.getDeviceId(), NOW)).thenReturn(true);
        service.setQuietService(quiet);
        service.dispatchDueReminders();
        assertThat(followUp.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        verifyNoInteractions(speech, gateway);
    }

    @Test
    void pausedPartnerCancelsTheQueuedCare() {
        var pause = mock(ProactivePauseService.class);
        when(pause.isPaused(followUp.getDeviceId(), followUp.getRoleId(), NOW)).thenReturn(true);
        service.setPauseService(pause);
        service.dispatchDueReminders();
        assertThat(followUp.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        verifyNoInteractions(speech, gateway);
    }

    @Test
    void differentActivePartnerDefersInsteadOfSpeakingPrivateTopic() {
        var roles = mock(CompanionRoleService.class);
        var active = mock(CompanionRoleService.RoleSnapshot.class);
        when(active.id()).thenReturn(UUID.randomUUID());
        when(roles.getActive(followUp.getDeviceId())).thenReturn(active);
        service.setRoleService(roles);
        service.dispatchDueReminders();
        assertThat(followUp.getScheduledAt()).isEqualTo(NOW.plusSeconds(60));
        verifyNoInteractions(speech, gateway);
    }

    @Test
    void offlineCareIgnoresOrdinaryMissedReminderPolicyAndExpiresWithoutReplay() {
        service.dispatchDueReminders();
        assertThat(followUp.getStatus()).isEqualTo(ReminderStatus.PENDING);
        Instant expired = NOW.plusSeconds(14400);
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(ReminderStatus.PENDING, expired))
                .thenReturn(List.of(followUp));
        new ReminderDeliveryService(repository, gateway, speech, Clock.fixed(expired, ZoneOffset.UTC)).dispatchDueReminders();
        assertThat(followUp.getStatus()).isEqualTo(ReminderStatus.EXPIRED);
        verifyNoInteractions(speech);
        verify(gateway, never()).speakReminder(any(), any(), anyString());
    }

    @Test
    void uncertainPlaybackIsTerminalInsteadOfAutomaticResend() {
        followUp.markDispatched("test-command", new byte[44], NOW.minusSeconds(600));
        when(repository.findAllByStatusAndLastAttemptAtBefore(ReminderStatus.DISPATCHED, NOW.minusSeconds(300)))
                .thenReturn(List.of(followUp));
        service.recoverStaleDispatches();
        assertThat(followUp.getStatus()).isEqualTo(ReminderStatus.FAILED);
        assertThat(followUp.getFailureCode()).isEqualTo("unconfirmed_delivery");
        verifyNoInteractions(speech, gateway);
    }

    @Test
    void dndBeyondExpiryTerminatesInsteadOfExtendingTheConsentWindow() {
        var settings = mock(InteractionSettingsService.class);
        var snapshot = mock(InteractionSettingsService.InteractionSettingsSnapshot.class);
        when(settings.resolve(followUp.getDeviceId())).thenReturn(snapshot);
        when(settings.isDnd(snapshot, NOW)).thenReturn(true);
        when(settings.nextDndEnd(snapshot, NOW)).thenReturn(NOW.plusSeconds(15000));
        var dndService = new ReminderDeliveryService(repository, gateway, speech, Clock.fixed(NOW, ZoneOffset.UTC),
                settings, null, null);
        dndService.dispatchDueReminders();
        assertThat(followUp.getStatus()).isEqualTo(ReminderStatus.EXPIRED);
        verifyNoInteractions(speech, gateway);
    }
}
