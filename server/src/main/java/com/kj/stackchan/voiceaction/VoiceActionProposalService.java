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
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.notification.InteractiveNotificationService;
import com.kj.stackchan.notification.NotificationResponseAction;
import com.kj.stackchan.workday.WorkdayCompanionService;
import com.kj.stackchan.workday.WorkdayRestAction;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ConversationService conversationService;
    private final CompanionRoleService roleService;
    private final InteractiveNotificationService notificationService;
    private final WorkdayCompanionService workdayCompanionService;
    private final PersonalTaskService personalTaskService;

    @Autowired
    public VoiceActionProposalService(VoiceActionProposalRepository proposalRepository,
                                      VoiceActionAuditRepository auditRepository,
                                      ReminderService reminderService,
                                      InteractionSettingsService settingsService,
                                      LongTermMemoryService memoryService,
                                      DeviceInteractionSettingsCoordinator settingsCoordinator,
                                      Clock clock, ConversationService conversationService,
                                      CompanionRoleService roleService,
                                      InteractiveNotificationService notificationService,
                                      WorkdayCompanionService workdayCompanionService,
                                      PersonalTaskService personalTaskService) {
        this.proposalRepository = proposalRepository;
        this.auditRepository = auditRepository;
        this.reminderService = reminderService;
        this.settingsService = settingsService;
        this.memoryService = memoryService;
        this.settingsCoordinator = settingsCoordinator;
        this.clock = clock;
        this.conversationService = conversationService;
        this.roleService = roleService;
        this.notificationService = notificationService;
        this.workdayCompanionService = workdayCompanionService;
        this.personalTaskService = personalTaskService;
    }

    public VoiceActionProposalService(VoiceActionProposalRepository proposalRepository,
                                      VoiceActionAuditRepository auditRepository,
                                      ReminderService reminderService,
                                      InteractionSettingsService settingsService,
                                      LongTermMemoryService memoryService,
                                      DeviceInteractionSettingsCoordinator settingsCoordinator,
                                      Clock clock, ConversationService conversationService,
                                      CompanionRoleService roleService,
                                      InteractiveNotificationService notificationService,
                                      WorkdayCompanionService workdayCompanionService) {
        this(proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, clock, conversationService, roleService, notificationService,
                workdayCompanionService, null);
    }

    public VoiceActionProposalService(VoiceActionProposalRepository proposalRepository,
                                      VoiceActionAuditRepository auditRepository,
                                      ReminderService reminderService, InteractionSettingsService settingsService,
                                      LongTermMemoryService memoryService,
                                      DeviceInteractionSettingsCoordinator settingsCoordinator, Clock clock) {
        this(proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, clock, null, null, null, null);
    }

    public VoiceActionProposalService(VoiceActionProposalRepository proposalRepository,
                                      VoiceActionAuditRepository auditRepository,
                                      ReminderService reminderService,
                                      InteractionSettingsService settingsService,
                                      LongTermMemoryService memoryService,
                                      DeviceInteractionSettingsCoordinator settingsCoordinator,
                                      Clock clock,
                                      ConversationService conversationService,
                                      CompanionRoleService roleService) {
        this(proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, clock, conversationService, roleService, null, null);
    }

    public VoiceActionProposalService(VoiceActionProposalRepository proposalRepository,
                                      VoiceActionAuditRepository auditRepository,
                                      ReminderService reminderService,
                                      InteractionSettingsService settingsService,
                                      LongTermMemoryService memoryService,
                                      DeviceInteractionSettingsCoordinator settingsCoordinator,
                                      Clock clock,
                                      ConversationService conversationService,
                                      CompanionRoleService roleService,
                                      InteractiveNotificationService notificationService) {
        this(proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, clock, conversationService, roleService, notificationService, null);
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
        if (draft.actionType() == VoiceActionType.CREATE_REMINDER && draft.targetReference() != null) {
            if (draft.durationMinutes() == null || draft.durationMinutes() < 1 || draft.durationMinutes() > 1440) {
                throw new VoiceActionException("Recent reminder delay is invalid");
            }
            var heard = reminderService.requireHeardUserReminder(draft.targetReference(), deviceId,
                    resolveRoleId(conversationId), draft.targetAt(), draft.content());
            draft = new VoiceActionDraft(VoiceActionType.CREATE_REMINDER, true, heard.content(), "再提醒",
                    clock.instant().plusSeconds(draft.durationMinutes() * 60L), heard.zoneId(), "NONE", 1,
                    draft.durationMinutes(), heard.lastCompletedAt(), null, null, heard.id());
        }
        if (draft.actionType() == VoiceActionType.START_WORKDAY_REST
                || draft.actionType() == VoiceActionType.SNOOZE_WORKDAY_REST
                || draft.actionType() == VoiceActionType.SKIP_WORKDAY_REST_FOR_DAY) {
            Instant promptAt = workdayCompanionService == null ? null : workdayCompanionService.pendingRestPromptAt(deviceId);
            if (promptAt == null) throw new VoiceActionException("No rest prompt is awaiting a response");
            if ("近期休息".equals(draft.title()) && (draft.targetAt() == null || promptAt.isAfter(draft.targetAt()))) {
                throw new VoiceActionException("The heard rest prompt is no longer current");
            }
            draft = new VoiceActionDraft(draft.actionType(), true, null, null, null, null, null, null,
                    null, promptAt, null, null, null);
        }
        NotificationResponseAction notificationAction = switch (draft.actionType()) {
            case ACKNOWLEDGE_NOTIFICATION -> NotificationResponseAction.ACKNOWLEDGE;
            case COMPLETE_NOTIFICATION -> NotificationResponseAction.COMPLETE;
            case SNOOZE_NOTIFICATION -> NotificationResponseAction.SNOOZE;
            default -> null;
        };
        if (notificationAction != null) {
            if (notificationService == null) throw new VoiceActionException("Interactive notifications are unavailable");
            String content = notificationService.descriptionForVoice(draft.targetReference(), deviceId,
                    resolveRoleId(conversationId), notificationAction);
            draft = new VoiceActionDraft(draft.actionType(), true, content, null, null, null, null, null,
                    draft.durationMinutes(), null, null, null, draft.targetReference());
        }
        if (draft.actionType() == VoiceActionType.SNOOZE_NEXT_REMINDER
                || draft.actionType() == VoiceActionType.SKIP_NEXT_REMINDER) {
            var target = reminderService.nextPendingUserReminder(deviceId, resolveRoleId(conversationId));
            if (target == null) throw new VoiceActionException("No pending reminder for this partner");
            // Resolve on the server, including model-created drafts; never trust a model-supplied ID.
            draft = new VoiceActionDraft(draft.actionType(), true, target.content(), null,
                    target.scheduledAt(), target.zoneId(), null, null, draft.durationMinutes(),
                    null, null, null, target.id());
        }
        Instant now = clock.instant();
        VoiceActionProposalEntity proposal = proposalRepository.save(
                new VoiceActionProposalEntity(SINGLE_ADMIN, deviceId, resolveRoleId(conversationId),
                        conversationId, turnId, draft, now, now.plus(TTL)));
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
            case CREATE_REMINDER -> "再提醒".equals(proposal.title())
                    ? "要在 " + proposal.durationMinutes() + " 分钟后再提醒一次：“" + proposal.content()
                        + "”。原来的周期和待办状态不变。确认执行吗？"
                    : "要创建提醒：" + proposal.content() + "，时间为 " + proposal.scheduledAt() + "。确认执行吗？";
            case SNOOZE_NEXT_REMINDER -> "要将提醒“" + proposal.content() + "”推迟 " + proposal.durationMinutes() + " 分钟。确认执行吗？";
            case SKIP_NEXT_REMINDER -> "要跳过提醒“" + proposal.content() + "”的下一次播报。确认执行吗？";
            case SET_TEMPORARY_DND -> "要将免打扰持续到 " + proposal.targetAt() + "。确认执行吗？";
            case SET_VOLUME -> "要将音量调到 " + proposal.volumePercent() + "%。确认执行吗？";
            case CREATE_MEMORY_SUGGESTION -> "已生成一条待确认记忆建议。";
            case SWITCH_ROLE -> "要切换到角色“" + proposal.content() + "”。确认执行吗？";
            case ACKNOWLEDGE_NOTIFICATION -> notificationLabel(proposal.content()) + "，要标记为已知晓。确认执行吗？";
            case SNOOZE_NOTIFICATION -> notificationLabel(proposal.content()) + "，要在 " + proposal.durationMinutes() + " 分钟后再次播报。确认执行吗？";
            case COMPLETE_NOTIFICATION -> notificationLabel(proposal.content()) + "，要向来源回报已完成，不会替你执行外部任务。确认执行吗？";
            case START_WORKDAY -> "要开始当前设备的工作模式。确认执行吗？";
            case END_WORKDAY -> "要结束当前设备的工作模式。确认执行吗？";
            case START_WORKDAY_REST -> "要开始本轮休息。确认执行吗？";
            case SNOOZE_WORKDAY_REST -> "要将休息提醒推迟十分钟。确认执行吗？";
            case SKIP_WORKDAY_REST_FOR_DAY -> "要在今天跳过后续休息提醒。确认执行吗？";
            case CREATE_PERSONAL_TASK -> proposal.scheduledAt() == null
                    ? "要新增待办“" + proposal.content() + "”。确认执行吗？"
                    : "要新增待办“" + proposal.content() + "”，截止时间为 " + proposal.scheduledAt() + "。确认执行吗？";
            case COMPLETE_PERSONAL_TASK -> "要将待办“" + proposal.content() + "”标记为完成。确认执行吗？";
        };
    }

    private ProposalSnapshot executeLocked(VoiceActionProposalEntity proposal, Instant now) {
        if (proposal.getStatus() == VoiceActionStatus.PENDING) {
            proposal.markExecuting(now);
        }
        try {
            if (proposal.getActionType() == VoiceActionType.CREATE_REMINDER && proposal.getTargetReference() != null) {
                reminderService.requireHeardUserReminder(proposal.getTargetReference(), proposal.getDeviceId(),
                        proposal.getRoleId(), proposal.getTargetAt(), proposal.getContent());
            }
            UUID result = switch (proposal.getActionType()) {
                case CREATE_REMINDER -> conversationService == null
                        ? reminderService.create(new ReminderService.ReminderCommand(
                        proposal.getDeviceId(), proposal.getContent(), proposal.getScheduledAt(), proposal.getZoneId(),
                        ReminderRecurrence.valueOf(proposal.getRecurrenceType()), proposal.getRecurrenceInterval())).id()
                        : reminderService.create(proposal.getRoleId(), new ReminderService.ReminderCommand(
                        proposal.getDeviceId(), proposal.getContent(), proposal.getScheduledAt(), proposal.getZoneId(),
                        ReminderRecurrence.valueOf(proposal.getRecurrenceType()), proposal.getRecurrenceInterval())).id();
                case SWITCH_ROLE -> roleService.switchActiveFromVoice(
                        proposal.getDeviceId(), proposal.getContent()).id();
                case SNOOZE_NEXT_REMINDER -> reminderService.applyConfirmedVoiceChange(proposal.getTargetReference(),
                        proposal.getDeviceId(), proposal.getRoleId(), proposal.getScheduledAt(), proposal.getContent(),
                        proposal.getDurationMinutes()).id();
                case SKIP_NEXT_REMINDER -> reminderService.applyConfirmedVoiceChange(proposal.getTargetReference(),
                        proposal.getDeviceId(), proposal.getRoleId(), proposal.getScheduledAt(), proposal.getContent(), null).id();
                case SET_TEMPORARY_DND -> settingsService.setTemporaryDndUntil(proposal.getDeviceId(), proposal.getTargetAt()).deviceId();
                case SET_VOLUME -> {
                    var settings = settingsService.setVolume(proposal.getDeviceId(), proposal.getVolumePercent());
                    settingsCoordinator.send(settings);
                    yield settings.deviceId();
                }
                case CREATE_MEMORY_SUGGESTION -> memoryService.suggest(proposal.getRoleId(), new LongTermMemoryService.MemorySuggestionCommand(
                        new LongTermMemoryService.MemoryCommand(MemoryScopeType.DEVICE, proposal.getDeviceId(),
                                MemoryCategory.valueOf(proposal.getMemoryCategory()), proposal.getTitle(), proposal.getContent()),
                        "voice_action_proposal",
                        proposal.getSourceTurnId())).id();
                case ACKNOWLEDGE_NOTIFICATION -> executeNotificationResponse(
                        proposal, NotificationResponseAction.ACKNOWLEDGE, null);
                case SNOOZE_NOTIFICATION -> executeNotificationResponse(
                        proposal, NotificationResponseAction.SNOOZE, proposal.getDurationMinutes());
                case COMPLETE_NOTIFICATION -> executeNotificationResponse(
                        proposal, NotificationResponseAction.COMPLETE, null);
                case START_WORKDAY -> workdayAction(proposal, null, true);
                case END_WORKDAY -> workdayAction(proposal, null, false);
                case START_WORKDAY_REST -> workdayAction(proposal, WorkdayRestAction.START_REST, null);
                case SNOOZE_WORKDAY_REST -> workdayAction(proposal, WorkdayRestAction.SNOOZE, null);
                case SKIP_WORKDAY_REST_FOR_DAY -> workdayAction(proposal, WorkdayRestAction.SKIP_FOR_DAY, null);
                case CREATE_PERSONAL_TASK -> {
                    if (personalTaskService == null) throw new VoiceActionException("Personal tasks are unavailable");
                    yield personalTaskService.create(proposal.getRoleId(), new PersonalTaskService.TaskCommand(
                            proposal.getDeviceId(), proposal.getContent(), null, PersonalTaskPriority.NORMAL,
                            proposal.getScheduledAt(), proposal.getZoneId() == null ? "Asia/Shanghai" : proposal.getZoneId())).id();
                }
                case COMPLETE_PERSONAL_TASK -> {
                    if (personalTaskService == null) throw new VoiceActionException("Personal tasks are unavailable");
                    yield personalTaskService.complete(
                            proposal.getTargetReference(), proposal.getDeviceId(), proposal.getRoleId()).id();
                }
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

    private String notificationLabel(String content) {
        if (content == null || content.isBlank()) return "这条通知";
        int length = content.codePointCount(0, content.length());
        return length <= 48 ? "通知“" + content + "”"
                : "通知内容开头是“" + content.substring(0, content.offsetByCodePoints(0, 48)) + "…”";
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
        if (draft.actionType() == VoiceActionType.SWITCH_ROLE
                && (draft.content() == null || draft.content().isBlank() || draft.content().length() > 80)) {
            throw new VoiceActionException("Voice role switch proposal is invalid");
        }
        if (draft.actionType() == VoiceActionType.CREATE_PERSONAL_TASK
                && (draft.content() == null || draft.content().isBlank() || draft.content().length() > 200
                || (draft.scheduledAt() != null && (draft.zoneId() == null || draft.zoneId().isBlank())))) {
            throw new VoiceActionException("Voice personal task proposal is invalid");
        }
        if (draft.actionType() == VoiceActionType.COMPLETE_PERSONAL_TASK
                && (draft.targetReference() == null || draft.content() == null || draft.content().isBlank())) {
            throw new VoiceActionException("Voice personal task completion is invalid");
        }
        if ((draft.actionType() == VoiceActionType.ACKNOWLEDGE_NOTIFICATION
                || draft.actionType() == VoiceActionType.COMPLETE_NOTIFICATION)
                && (draft.targetReference() == null || draft.durationMinutes() != null)) {
            throw new VoiceActionException("Voice notification response proposal is invalid");
        }
        if (draft.actionType() == VoiceActionType.SNOOZE_NOTIFICATION
                && (draft.targetReference() == null || draft.durationMinutes() == null
                || draft.durationMinutes() < 1 || draft.durationMinutes() > 1440)) {
            throw new VoiceActionException("Voice notification snooze proposal is invalid");
        }
    }

    private UUID resolveRoleId(UUID conversationId) {
        return conversationService == null
                ? com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID
                : conversationService.roleId(conversationId);
    }

    private UUID executeNotificationResponse(
            VoiceActionProposalEntity proposal,
            NotificationResponseAction action,
            Integer snoozeMinutes
    ) {
        if (notificationService == null) throw new VoiceActionException("Interactive notifications are unavailable");
        notificationService.respond(proposal.getTargetReference(), proposal.getDeviceId(), proposal.getRoleId(),
                action, snoozeMinutes);
        return proposal.getTargetReference();
    }

    private UUID workdayAction(
            VoiceActionProposalEntity proposal,
            WorkdayRestAction restAction,
            Boolean start
    ) {
        if (workdayCompanionService == null) throw new VoiceActionException("Workday companion is unavailable");
        if (restAction != null) workdayCompanionService.respondToRest(proposal.getDeviceId(), restAction, proposal.getTargetAt());
        else if (Boolean.TRUE.equals(start)) workdayCompanionService.start(proposal.getDeviceId());
        else workdayCompanionService.stop(proposal.getDeviceId());
        return proposal.getDeviceId();
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
