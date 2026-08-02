package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.device.DeviceInteractionSettingsCoordinator;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.memory.MemoryCategory;
import com.kj.stackchan.memory.MemoryScopeType;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceActionProposalService {
    public static final String SINGLE_ADMIN = "single-admin";
    private static final Duration TTL = Duration.ofMinutes(2);

    private final VoiceActionProposalRepository proposalRepository;
    private final VoiceActionAuditRepository auditRepository;
    private final ReminderService reminderService;
    private final InteractionSettingsService settingsService;
    private final LongTermMemoryService memoryService;
    private final DeviceInteractionSettingsCoordinator settingsCoordinator;
    private final Clock clock;

    public VoiceActionProposalService(VoiceActionProposalRepository proposalRepository,
                                      VoiceActionAuditRepository auditRepository,
                                      ReminderService reminderService,
                                      InteractionSettingsService settingsService,
                                      LongTermMemoryService memoryService,
                                      DeviceInteractionSettingsCoordinator settingsCoordinator,
                                      Clock clock) {
        this.proposalRepository = proposalRepository;
        this.auditRepository = auditRepository;
        this.reminderService = reminderService;
        this.settingsService = settingsService;
        this.memoryService = memoryService;
        this.settingsCoordinator = settingsCoordinator;
        this.clock = clock;
    }

    @Transactional
    public ProposalSnapshot propose(UUID deviceId, UUID conversationId, UUID turnId, VoiceActionDraft draft) {
        VoiceActionProposalEntity proposal = persist(deviceId, conversationId, turnId, draft);
        if (!draft.confirmationRequired()) {
            return executeLocked(proposal, clock.instant());
        }
        return snapshot(proposal);
    }

    @Transactional
    public ProposalSnapshot submit(UUID deviceId, UUID conversationId, UUID turnId, VoiceActionDraft draft) {
        return snapshot(persist(deviceId, conversationId, turnId, draft));
    }

    private VoiceActionProposalEntity persist(UUID deviceId, UUID conversationId, UUID turnId, VoiceActionDraft draft) {
        if (deviceId == null || conversationId == null || turnId == null || draft == null || draft.actionType() == null) {
            throw new VoiceActionException("Voice action proposal is invalid");
        }
        validateDraft(draft);
        Instant now = clock.instant();
        VoiceActionProposalEntity proposal = proposalRepository.save(
                new VoiceActionProposalEntity(SINGLE_ADMIN, deviceId, conversationId, turnId, draft, now, now.plus(TTL)));
        auditRepository.save(new VoiceActionAuditEntity(proposal, VoiceActionAuditEvent.PROPOSED, null, now));
        return proposal;
    }

    @Transactional(readOnly = true)
    public ProposalSnapshot latestPending(UUID deviceId, UUID conversationId) {
        return proposalRepository.findFirstByActorIdAndDeviceIdAndConversationIdAndStatusOrderByCreatedAtDesc(
                        SINGLE_ADMIN, deviceId, conversationId, VoiceActionStatus.PENDING)
                .map(this::snapshot).orElse(null);
    }

    @Transactional
    public ProposalSnapshot confirm(UUID proposalId, UUID deviceId, UUID conversationId) {
        VoiceActionProposalEntity proposal = findScopedForUpdate(proposalId, deviceId, conversationId);
        Instant now = clock.instant();
        if (proposal.getStatus() == VoiceActionStatus.EXECUTED || proposal.getStatus() == VoiceActionStatus.FAILED
                || proposal.getStatus() == VoiceActionStatus.CANCELLED || proposal.getStatus() == VoiceActionStatus.EXPIRED) {
            return snapshot(proposal);
        }
        if (!proposal.isConfirmationRequired()) {
            return snapshot(proposal);
        }
        if (!proposal.getExpiresAt().isAfter(now)) {
            proposal.markExpired(now);
            auditRepository.save(new VoiceActionAuditEntity(proposal, VoiceActionAuditEvent.EXPIRED, null, now));
            return snapshot(proposal);
        }
        proposal.markExecuting(now);
        auditRepository.save(new VoiceActionAuditEntity(proposal, VoiceActionAuditEvent.CONFIRMED, null, now));
        return executeLocked(proposal, now);
    }

    @Transactional
    public ProposalSnapshot cancel(UUID proposalId, UUID deviceId, UUID conversationId) {
        VoiceActionProposalEntity proposal = findScopedForUpdate(proposalId, deviceId, conversationId);
        if (proposal.getStatus() == VoiceActionStatus.PENDING) {
            Instant now = clock.instant();
            proposal.markCancelled(now);
            auditRepository.save(new VoiceActionAuditEntity(proposal, VoiceActionAuditEvent.CANCELLED, null, now));
        }
        return snapshot(proposal);
    }

    @Transactional
    public ProposalSnapshot executeMemorySuggestion(UUID proposalId, UUID deviceId, UUID conversationId) {
        VoiceActionProposalEntity proposal = findScopedForUpdate(proposalId, deviceId, conversationId);
        if (proposal.getActionType() != VoiceActionType.CREATE_MEMORY_SUGGESTION
                || proposal.isConfirmationRequired()) {
            throw new VoiceActionException("Voice action proposal is not a memory suggestion");
        }
        if (proposal.getStatus() == VoiceActionStatus.PENDING) {
            return executeLocked(proposal, clock.instant());
        }
        return snapshot(proposal);
    }

    public String restatement(ProposalSnapshot proposal) {
        return switch (proposal.actionType()) {
            case CREATE_REMINDER -> "要创建提醒：" + proposal.content() + "，时间为 " + proposal.scheduledAt() + "。确认执行吗？";
            case SNOOZE_NEXT_REMINDER -> "要将下一条提醒推迟 " + proposal.durationMinutes() + " 分钟。确认执行吗？";
            case SKIP_NEXT_REMINDER -> "要跳过下一次提醒。确认执行吗？";
            case SET_TEMPORARY_DND -> "要将免打扰持续到 " + proposal.targetAt() + "。确认执行吗？";
            case SET_VOLUME -> "要将音量调到 " + proposal.volumePercent() + "%。确认执行吗？";
            case CREATE_MEMORY_SUGGESTION -> "已生成一条待确认记忆建议。";
        };
    }

    private ProposalSnapshot executeLocked(VoiceActionProposalEntity proposal, Instant now) {
        if (proposal.getStatus() == VoiceActionStatus.PENDING) {
            proposal.markExecuting(now);
        }
        try {
            UUID result = switch (proposal.getActionType()) {
                case CREATE_REMINDER -> reminderService.create(new ReminderService.ReminderCommand(
                        proposal.getDeviceId(), proposal.getContent(), proposal.getScheduledAt(), proposal.getZoneId(),
                        ReminderRecurrence.valueOf(proposal.getRecurrenceType()), proposal.getRecurrenceInterval())).id();
                case SNOOZE_NEXT_REMINDER -> reminderService.snoozeNext(proposal.getDeviceId(), proposal.getDurationMinutes()).id();
                case SKIP_NEXT_REMINDER -> reminderService.skipNextPending(proposal.getDeviceId()).id();
                case SET_TEMPORARY_DND -> settingsService.setTemporaryDndUntil(proposal.getDeviceId(), proposal.getTargetAt()).deviceId();
                case SET_VOLUME -> {
                    var settings = settingsService.setVolume(proposal.getDeviceId(), proposal.getVolumePercent());
                    settingsCoordinator.send(settings);
                    yield settings.deviceId();
                }
                case CREATE_MEMORY_SUGGESTION -> memoryService.suggest(new LongTermMemoryService.MemorySuggestionCommand(
                        new LongTermMemoryService.MemoryCommand(MemoryScopeType.DEVICE, proposal.getDeviceId(),
                                MemoryCategory.valueOf(proposal.getMemoryCategory()), proposal.getTitle(), proposal.getContent()),
                        "voice_action_proposal")).id();
            };
            Instant completed = clock.instant();
            proposal.markExecuted(result, completed);
            auditRepository.save(new VoiceActionAuditEntity(proposal, VoiceActionAuditEvent.EXECUTED, null, completed));
        } catch (RuntimeException exception) {
            Instant failed = clock.instant();
            proposal.markFailed("action_failed", failed);
            auditRepository.save(new VoiceActionAuditEntity(proposal, VoiceActionAuditEvent.FAILED, "action_failed", failed));
        }
        return snapshot(proposal);
    }

    private VoiceActionProposalEntity findScopedForUpdate(UUID id, UUID deviceId, UUID conversationId) {
        VoiceActionProposalEntity proposal = proposalRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new VoiceActionException("Voice action proposal not found"));
        if (!SINGLE_ADMIN.equals(proposal.getActorId()) || !proposal.getDeviceId().equals(deviceId)
                || !proposal.getConversationId().equals(conversationId)) {
            throw new VoiceActionException("Voice action proposal is unauthorized");
        }
        return proposal;
    }

    private void validateDraft(VoiceActionDraft draft) {
        if (draft.actionType() != VoiceActionType.CREATE_MEMORY_SUGGESTION && !draft.confirmationRequired()) {
            throw new VoiceActionException("Voice action confirmation is required");
        }
        if ((draft.actionType() == VoiceActionType.SNOOZE_NEXT_REMINDER)
                && (draft.durationMinutes() == null || draft.durationMinutes() < 1 || draft.durationMinutes() > 1440)) {
            throw new VoiceActionException("Voice action snooze duration is invalid");
        }
        if (draft.actionType() == VoiceActionType.SET_VOLUME
                && (draft.volumePercent() == null || draft.volumePercent() < 0 || draft.volumePercent() > 100)) {
            throw new VoiceActionException("Voice action volume is invalid");
        }
        if (draft.actionType() == VoiceActionType.SET_TEMPORARY_DND
                && (draft.targetAt() == null || !draft.targetAt().isAfter(clock.instant())
                || draft.targetAt().isAfter(clock.instant().plus(Duration.ofHours(24))))) {
            throw new VoiceActionException("Voice action DND time is invalid");
        }
        if (draft.actionType() == VoiceActionType.CREATE_MEMORY_SUGGESTION
                && (draft.confirmationRequired() || draft.content() == null || draft.content().isBlank()
                || draft.memoryCategory() == null)) {
            throw new VoiceActionException("Voice memory suggestion is invalid");
        }
        if (draft.actionType() == VoiceActionType.CREATE_REMINDER
                && (draft.content() == null || draft.content().isBlank() || draft.scheduledAt() == null
                || draft.zoneId() == null || draft.recurrenceType() == null || draft.recurrenceInterval() == null)) {
            throw new VoiceActionException("Voice reminder proposal is invalid");
        }
    }

    private ProposalSnapshot snapshot(VoiceActionProposalEntity proposal) {
        return new ProposalSnapshot(proposal.getId(), proposal.getActionType(), proposal.getStatus(),
                proposal.isConfirmationRequired(), proposal.getContent(), proposal.getTitle(), proposal.getScheduledAt(),
                proposal.getDurationMinutes(), proposal.getTargetAt(), proposal.getVolumePercent(), proposal.getResultReference(),
                proposal.getFailureCode(), proposal.getExpiresAt());
    }

    public record ProposalSnapshot(UUID id, VoiceActionType actionType, VoiceActionStatus status,
                                   boolean confirmationRequired, String content, String title, Instant scheduledAt,
                                   Integer durationMinutes, Instant targetAt, Integer volumePercent, UUID resultReference,
                                   String failureCode, Instant expiresAt) { }
}
