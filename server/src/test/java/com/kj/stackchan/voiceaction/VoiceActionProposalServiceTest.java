package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kj.stackchan.device.DeviceInteractionSettingsCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.notification.InteractiveNotificationService;
import com.kj.stackchan.notification.NotificationResponseAction;
import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.role.CompanionRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceActionProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T08:00:00Z");
    @Mock private VoiceActionProposalRepository proposalRepository;
    @Mock private VoiceActionAuditRepository auditRepository;
    @Mock private ReminderService reminderService;
    @Mock private InteractionSettingsService settingsService;
    @Mock private LongTermMemoryService memoryService;
    @Mock private DeviceInteractionSettingsCoordinator settingsCoordinator;
    private VoiceActionProposalService service;

    @BeforeEach
    void setUp() {
        when(proposalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new VoiceActionProposalService(proposalRepository, auditRepository, reminderService,
                settingsService, memoryService, settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirmationExecutesReminderExactlyOnceAndReplayIsIdempotent() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = service.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        verifyNoInteractions(reminderService);

        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));
        UUID reminderId = UUID.randomUUID();
        when(reminderService.create(any())).thenReturn(new ReminderService.ReminderSnapshot(
                reminderId, deviceId, "喝水", NOW.plusSeconds(600), "Asia/Shanghai",
                ReminderStatus.PENDING, 0, null, NOW, NOW));

        assertThat(service.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(service.confirm(proposal.id(), deviceId, conversationId).resultReference())
                .isEqualTo(reminderId);
        verify(reminderService, times(1)).create(any());
    }

    @Test
    void cancellationPreventsLaterExecution() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = service.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(service.cancel(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.CANCELLED);
        assertThat(service.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.CANCELLED);
        verifyNoInteractions(reminderService);
    }

    @Test
    void crossDeviceConfirmationIsRejectedBeforeExecution() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = service.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.confirm(proposal.id(), UUID.randomUUID(), conversationId))
                .isInstanceOf(VoiceActionException.class)
                .hasMessageContaining("unauthorized");
        verifyNoInteractions(reminderService);
    }

    @Test
    void expiredProposalCannotExecute() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(121));
        VoiceActionProposalService expiringService = new VoiceActionProposalService(
                proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, clock);
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = expiringService.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(expiringService.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXPIRED);
        verifyNoInteractions(reminderService);
    }

    @Test
    void proposalToolPersistsMemoryProposalWithoutCallingMemoryService() {
        VoiceActionProposalTool tool = new VoiceActionProposalTool(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), service, new ObjectMapper());

        String result = tool.submit(new VoiceActionProposalTool.Input(
                "CREATE_MEMORY_SUGGESTION", "喜欢爵士乐", "偏好", null, null,
                null, null, null, null, null, "USER_PROFILE"));

        assertThat(result).contains("CREATE_MEMORY_SUGGESTION").contains("PENDING");
        assertThat(tool.submittedProposal().status()).isEqualTo(VoiceActionStatus.PENDING);
        verifyNoInteractions(memoryService);
    }

    @Test
    void confirmedNotificationResponseUsesPersistedTrustedScopeExactlyOnce() {
        ConversationService conversationService = mock(ConversationService.class);
        CompanionRoleService roleService = mock(CompanionRoleService.class);
        InteractiveNotificationService notificationService = mock(InteractiveNotificationService.class);
        VoiceActionProposalService interactiveService = new VoiceActionProposalService(
                proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC), conversationService, roleService,
                notificationService);
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);

        var proposal = interactiveService.propose(deviceId, conversationId, UUID.randomUUID(),
                VoiceActionDraft.notificationResponse(
                        VoiceActionType.SNOOZE_NOTIFICATION, notificationId, 15));
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(interactiveService.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(interactiveService.confirm(proposal.id(), deviceId, conversationId).resultReference())
                .isEqualTo(notificationId);
        verify(notificationService, times(1)).respond(
                notificationId, deviceId, roleId, NotificationResponseAction.SNOOZE, 15);
    }

    private VoiceActionDraft reminderDraft() {
        return VoiceActionDraft.reminder("喝水", NOW.plusSeconds(600), "Asia/Shanghai", "NONE", 1);
    }

    private VoiceActionProposalEntity capturedProposal() {
        var captor = org.mockito.ArgumentCaptor.forClass(VoiceActionProposalEntity.class);
        verify(proposalRepository).save(captor.capture());
        return captor.getValue();
    }
}
