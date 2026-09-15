package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.ProactiveTopicCooldownService;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.memory.MemoryCategory;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.notification.InteractiveNotificationService;
import com.kj.stackchan.notification.NotificationResponseAction;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.task.PersonalTaskService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class VoiceActionCoordinator {
    public void cancelPendingOperation(UUID deviceId, UUID conversationId) {
        var pending = proposalService.latestPending(deviceId, conversationId);
        if (pending != null) proposalService.cancel(pending.id(), deviceId, conversationId);
    }
    private static final Pattern VOLUME = Pattern.compile("音量(?:调到|设置为|设为)\\s*(\\d{1,3})\\s*%?");
    private static final Pattern DND_MINUTES = Pattern.compile("(?:安静|免打扰)(?:到|持续)\\s*(\\d{1,4})\\s*分钟");
    private static final Pattern REMINDER_MINUTES = Pattern.compile("提醒我\\s*(.+?)\\s*(\\d{1,5})\\s*分钟后");
    private static final Pattern SNOOZE_MINUTES = Pattern.compile("(?:稍后|推迟|延后)\\s*(\\d{1,4})\\s*分钟");
    private static final Pattern SWITCH_ROLE = Pattern.compile("切换到(?:角色)?[“\"']?([^”\"'，,。！!]+)[”\"']?");
    private static final Pattern CREATE_TASK = Pattern.compile(
            "^(?:添加|新增|新建|记下|记个)(?:一个)?(?:待办|任务)[：:,，]?\\s*(.+?)[。！!]?$"
    );
    private static final Pattern COMPLETE_TASK = Pattern.compile(
            "^(?:完成|办完|标记完成)(?:这个|一条)?(?:待办|任务)[：:,，]?\\s*[“\"']?(.+?)[”\"']?[。！!]?$"
    );
    private static final Pattern TASK_TIME = Pattern.compile(
            "(今天|明天|后天|周[一二三四五六日天]|星期|\\d{1,2}[点时]|分钟后|小时后|天后|截止|到期)"
    );
    private static final Pattern PERSONAL_TASK_ACTION = Pattern.compile(
            "(?:添加|新增|新建|创建|记下|记个).*(?:待办|任务)|(?:完成|办完|标记完成).*(?:待办|任务)"
    );

    private final VoiceActionProposalService proposalService;
    private final ReminderService reminderService;
    private final LongTermMemoryService memoryService;
    private final InteractionSettingsService settingsService;
    private final Clock clock;
    private final VoiceActionProposalOrchestrator proposalOrchestrator;
    private final ProactiveTopicCooldownService topicCooldownService;
    private final InteractiveNotificationService notificationService;
    private final ConversationService conversationService;
    private final PersonalTaskService personalTaskService;
    private com.kj.stackchan.interaction.ProactivePauseService pauseService;

    @Autowired
    public void setPauseService(com.kj.stackchan.interaction.ProactivePauseService pauseService) { this.pauseService = pauseService; }

    private static final Pattern PAUSE_TODAY = Pattern.compile("^(?:(?:今天|今日)(?:别|不要)(?:再)?主动(?:聊|聊天|找我聊天)[了。！!]*|今天安静(?:一)?点[。！!]*)$");
    private static final Pattern PAUSE_MINUTES = Pattern.compile("^暂停主动(?:聊天|开场)\\s*(\\d{1,4})\\s*分钟[。！!]*$");
    private static final Pattern RESUME_PROACTIVE = Pattern.compile("^(?:恢复主动(?:聊天|开场)|可以继续主动(?:聊天|找我聊天)了)[。！!]*$");

    @Autowired
    public VoiceActionCoordinator(VoiceActionProposalService proposalService, ReminderService reminderService,
                                  LongTermMemoryService memoryService, InteractionSettingsService settingsService,
                                  Clock clock, VoiceActionProposalOrchestrator proposalOrchestrator,
                                  ProactiveTopicCooldownService topicCooldownService,
                                  InteractiveNotificationService notificationService,
                                  ConversationService conversationService,
                                  PersonalTaskService personalTaskService) {
        this.proposalService = proposalService;
        this.reminderService = reminderService;
        this.memoryService = memoryService;
        this.settingsService = settingsService;
        this.clock = clock;
        this.proposalOrchestrator = proposalOrchestrator;
        this.topicCooldownService = topicCooldownService;
        this.notificationService = notificationService;
        this.conversationService = conversationService;
        this.personalTaskService = personalTaskService;
    }

    public VoiceActionCoordinator(VoiceActionProposalService proposalService, ReminderService reminderService,
                                  LongTermMemoryService memoryService, InteractionSettingsService settingsService,
                                  Clock clock, VoiceActionProposalOrchestrator proposalOrchestrator,
                                  ProactiveTopicCooldownService topicCooldownService,
                                  InteractiveNotificationService notificationService,
                                  ConversationService conversationService) {
        this(proposalService, reminderService, memoryService, settingsService, clock, proposalOrchestrator,
                topicCooldownService, notificationService, conversationService, null);
    }

    public VoiceActionCoordinator(VoiceActionProposalService proposalService, ReminderService reminderService,
                                  LongTermMemoryService memoryService, InteractionSettingsService settingsService,
                                  Clock clock, VoiceActionProposalOrchestrator proposalOrchestrator) {
        this(proposalService, reminderService, memoryService, settingsService, clock, proposalOrchestrator,
                null, null, null);
    }

    public VoiceActionCoordinator(VoiceActionProposalService proposalService, ReminderService reminderService,
                                  LongTermMemoryService memoryService, InteractionSettingsService settingsService,
                                  Clock clock, VoiceActionProposalOrchestrator proposalOrchestrator,
                                  ProactiveTopicCooldownService topicCooldownService) {
        this(proposalService, reminderService, memoryService, settingsService, clock, proposalOrchestrator,
                topicCooldownService, null, null);
    }

    public ActionResult handle(UUID deviceId, UUID conversationId, UUID turnId, String transcript) {
        String text = transcript == null ? "" : transcript.trim();
        if (text.isBlank()) return null;
        if (pauseService != null) {
            var minutes = PAUSE_MINUTES.matcher(text);
            boolean today = PAUSE_TODAY.matcher(text).matches();
            boolean resume = RESUME_PROACTIVE.matcher(text).matches();
            if (today || resume || minutes.matches()) {
                UUID role = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : conversationService.roleId(conversationId);
                if (resume) {
                    cancelPendingOperation(deviceId, conversationId);
                    pauseService.resume(deviceId, role);
                    return new ActionResult("已解除我在这台设备上的主动聊天暂停，仍会遵守原来的时段和次数设置。", true);
                }
                Integer duration = today ? null : Integer.valueOf(minutes.group(1));
                if (duration != null && (duration < 1 || duration > 1440)) {
                    return new ActionResult("暂停时间可以是 1 到 1440 分钟。", true);
                }
                cancelPendingOperation(deviceId, conversationId);
                pauseService.pause(deviceId, role, duration);
                return new ActionResult(today ? "好的，我今天不再主动开场，设备当地时间明天恢复。提醒照常。"
                        : "好的，我暂停主动聊天 " + duration + " 分钟。提醒照常。", true);
            }
        }
        if (isMuteLastTopic(text) && topicCooldownService != null) {
            UUID roleId = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID
                    : conversationService.roleId(conversationId);
            Instant boundary = conversationService == null ? null : conversationService.voiceTopicBoundary(conversationId);
            boolean muted = topicCooldownService.muteLastDeliveredTopic(deviceId, roleId, boundary);
            return new ActionResult(
                    muted ? "好的，我不会再主动提这个话题。" : "我没找到近期播放过、可以停止主动提及的话题，这次没有修改话题偏好。",
                    true
            );
        }
        VoiceActionProposalService.ProposalSnapshot pending = proposalService.latestPending(deviceId, conversationId);
        if (isCancel(text) && pending != null) {
            proposalService.cancel(pending.id(), deviceId, conversationId);
            return new ActionResult("已取消这次操作。", true);
        }
        if (isConfirm(text) && pending != null) {
            VoiceActionProposalService.ProposalSnapshot executed = proposalService.confirm(pending.id(), deviceId, conversationId);
            return new ActionResult(executed.status() == VoiceActionStatus.EXECUTED ? "已执行。" : statusReply(executed), true);
        }
        if (pending != null) {
            return new ActionResult(proposalService.restatement(pending), true);
        }
        ActionResult personalTaskAction = proposePersonalTaskAction(deviceId, conversationId, turnId, text);
        if (personalTaskAction != null) return personalTaskAction;
        ActionResult recentResponse = proposeRecentResponse(deviceId, conversationId, turnId, text);
        if (recentResponse != null) return recentResponse;
        ActionResult notificationResponse = proposeNotificationResponse(deviceId, conversationId, turnId, text);
        if (notificationResponse != null) return notificationResponse;
        VoiceActionType workdayAction = workdayAction(text);
        if (workdayAction != null) {
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(
                    deviceId, conversationId, turnId,
                    new VoiceActionDraft(workdayAction, true, null, null, null, null,
                            null, null, null, null, null, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        Matcher switchRole = SWITCH_ROLE.matcher(text);
        if (switchRole.find()) {
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(
                    deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SWITCH_ROLE, true, switchRole.group(1).trim(), null,
                            null, null, null, null, null, null, null, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        Matcher snooze = SNOOZE_MINUTES.matcher(text);
        if (snooze.find() && text.contains("提醒")) {
            UUID role = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : conversationService.roleId(conversationId);
            if (reminderService.nextPendingUserReminder(deviceId, role) == null) {
                return new ActionResult("当前伙伴没有可以推迟的普通提醒。", true);
            }
            int minutes = Integer.parseInt(snooze.group(1));
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SNOOZE_NEXT_REMINDER, true, null, null, null, null, null,
                            null, minutes, null, null, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        if ((text.contains("跳过") || text.contains("略过")) && text.contains("提醒")) {
            UUID role = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : conversationService.roleId(conversationId);
            if (reminderService.nextPendingUserReminder(deviceId, role) == null) {
                return new ActionResult("当前伙伴没有可以跳过的普通提醒。", true);
            }
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SKIP_NEXT_REMINDER, true, null, null, null, null, null,
                            null, null, null, null, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }

        Matcher volume = VOLUME.matcher(text);
        if (volume.find()) {
            int value = Integer.parseInt(volume.group(1));
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SET_VOLUME, true, null, null, null, null, null, null,
                            null, null, value, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        Matcher dnd = DND_MINUTES.matcher(text);
        if (dnd.find()) {
            int minutes = Integer.parseInt(dnd.group(1));
            Instant until = clock.instant().plus(Duration.ofMinutes(minutes));
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SET_TEMPORARY_DND, true, null, null, null, null, null, null,
                            minutes, until, null, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        Matcher reminder = REMINDER_MINUTES.matcher(text);
        if (reminder.find()) {
            int minutes = Integer.parseInt(reminder.group(2));
            String content = reminder.group(1).trim();
            String zone = settingsService.resolve(deviceId).zoneId();
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    VoiceActionDraft.reminder(content, clock.instant().plus(Duration.ofMinutes(minutes)), zone,
                            ReminderRecurrence.NONE.name(), 1));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        if (text.startsWith("记住") || text.startsWith("请记住")) {
            String content = text.replaceFirst("^(请)?记住[：:，,]?\\s*", "").trim();
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.CREATE_MEMORY_SUGGESTION, false, content, "语音记忆建议",
                            null, null, null, null, null, null, null, MemoryCategory.USER_PROFILE.name(), null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        if (text.contains("下一条提醒") || text.contains("下一个提醒")) {
            UUID role = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : conversationService.roleId(conversationId);
            ReminderService.ReminderSnapshot next = reminderService.nextPending(deviceId, role);
            return new ActionResult(next == null ? "当前没有待处理提醒。" : "下一条提醒是：" + next.content() + "，时间为 " + next.scheduledAt() + "。", true);
        }
        if (text.contains("待确认记忆") || text.contains("待确认的记忆")) {
            UUID role = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : conversationService.roleId(conversationId);
            return new ActionResult("当前伙伴有 " + memoryService.pendingVisibleCount(role, deviceId) + " 条待确认记忆。", true);
        }
        if (isExplicitAction(text) && proposalOrchestrator != null) {
            String zone = settingsService.resolve(deviceId).zoneId();
            VoiceActionProposalService.ProposalSnapshot proposal = proposalOrchestrator.propose(
                    deviceId, conversationId, turnId, zone, text);
            if (proposal != null && proposal.actionType() == VoiceActionType.CREATE_MEMORY_SUGGESTION) {
                proposal = proposalService.executeMemorySuggestion(proposal.id(), deviceId, conversationId);
            }
            if (proposal != null) {
                return new ActionResult(proposalService.restatement(proposal), true);
            }
        }
        return null;
    }

    private boolean isConfirm(String text) { return text.matches("^(确认|确定|执行|好的|好|可以|是的)[。！!,.，]?$"); }

    private ActionResult proposeRecentResponse(UUID deviceId, UUID conversationId, UUID turnId, String text) {
        var snooze = Pattern.compile("^(?:这个|刚才的|刚才那条)?(?:稍后|推迟|延后)(?:\\s*(\\d{1,4}|十)\\s*分钟)?(?:再提醒我|再说|再提醒)?[。！!,.，]?$").matcher(text);
        boolean delay = snooze.matches();
        boolean acknowledged = text.matches("^(?:知道了|已知晓|我知道了)[。！!,.，]?$");
        boolean complete = text.matches("^(?:完成了|已完成|办完了)[。！!,.，]?$");
        if (!delay && !acknowledged && !complete) return null;
        UUID role = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : conversationService.roleId(conversationId);
        var heard = reminderService.latestHeard(deviceId, role,
                conversationService == null ? null : conversationService.voiceTopicBoundary(conversationId));
        if (heard == null) {
            return acknowledged ? null : new ActionResult("请说清楚要处理哪条提醒、通知或休息提示。", true);
        }
        if (heard.source() == com.kj.stackchan.reminder.ReminderSource.EXTERNAL) {
            if (!delay) return null;
            int minutes = snooze.group(1) == null || "十".equals(snooze.group(1)) ? 10 : Integer.parseInt(snooze.group(1));
            return proposeNotificationResponse(deviceId, conversationId, turnId, "这个通知稍后" + minutes + "分钟");
        }
        if (acknowledged) return new ActionResult("好的。", true);
        if (complete) return new ActionResult("播报本身没有待办完成状态。要完成待办，请说“完成待办”加上具体标题。", true);
        int minutes = snooze.group(1) == null || "十".equals(snooze.group(1)) ? 10 : Integer.parseInt(snooze.group(1));
        if (minutes < 1 || minutes > 1440) return new ActionResult("稍后时间可以是 1 到 1440 分钟。", true);
        if (heard.source() == com.kj.stackchan.reminder.ReminderSource.USER) {
            var draft = new VoiceActionDraft(VoiceActionType.CREATE_REMINDER, true, heard.content(), "再提醒",
                    clock.instant().plusSeconds(minutes * 60L), heard.zoneId(), "NONE", 1, minutes,
                    heard.lastCompletedAt(), null, null, heard.id());
            return new ActionResult(proposalService.restatement(proposalService.propose(deviceId, conversationId, turnId, draft)), true);
        }
        if (heard.proactiveTopicKey() != null && heard.proactiveTopicKey().startsWith("workday:rest:")) {
            if (minutes != 10) return new ActionResult("这轮休息可以推迟十分钟；请说“稍后十分钟再休息”。", true);
            var draft = new VoiceActionDraft(VoiceActionType.SNOOZE_WORKDAY_REST, true, null, "近期休息",
                    null, null, null, null, null, heard.lastCompletedAt(), null, null, null);
            try {
                return new ActionResult(proposalService.restatement(proposalService.propose(deviceId, conversationId, turnId, draft)), true);
            } catch (VoiceActionException exception) {
                return new ActionResult("刚才那轮休息提示已经失效，这次没有修改工作状态。", true);
            }
        }
        return new ActionResult("好的，先不展开这条消息。", true);
    }
    private boolean isCancel(String text) { return text.matches("^(取消|不用了|不要|算了)[。！!,.，]?$"); }
    private boolean isExplicitAction(String text) {
        return text.contains("提醒我") || text.contains("稍后提醒") || text.contains("跳过下一次")
                || text.contains("音量调到") || text.contains("安静到") || text.startsWith("记住")
                || text.startsWith("请记住") || text.contains("切换到角色")
                || PERSONAL_TASK_ACTION.matcher(text).find();
    }
    private ActionResult proposePersonalTaskAction(UUID deviceId, UUID conversationId, UUID turnId, String text) {
        if (personalTaskService == null) return null;
        UUID roleId = conversationService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID
                : conversationService.roleId(conversationId);
        Matcher complete = COMPLETE_TASK.matcher(text);
        if (complete.matches()) {
            PersonalTaskService.TitleMatch match = personalTaskService.matchOpenTitle(
                    deviceId, roleId, complete.group(1).trim());
            if (match.status() == PersonalTaskService.TitleMatchStatus.NOT_FOUND) {
                return new ActionResult("没有找到匹配的未完成待办。", true);
            }
            if (match.status() == PersonalTaskService.TitleMatchStatus.AMBIGUOUS) {
                return new ActionResult("找到了多条同名或相似待办，请说出更完整的标题。", true);
            }
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(
                    deviceId, conversationId, turnId,
                    VoiceActionDraft.completePersonalTask(match.task().id(), match.task().title()));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        Matcher create = CREATE_TASK.matcher(text);
        if (create.matches() && !TASK_TIME.matcher(text).find()) {
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(
                    deviceId, conversationId, turnId,
                    VoiceActionDraft.personalTask(create.group(1).trim(), null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        return null;
    }
    private VoiceActionType workdayAction(String text) {
        if (text.matches("^(?:开始|进入|开启)(?:今天的)?工作(?:模式)?[。！!,.，]?$")) {
            return VoiceActionType.START_WORKDAY;
        }
        if (text.matches("^(?:结束|退出|关闭)(?:今天的)?工作(?:模式)?[。！!,.，]?$")) {
            return VoiceActionType.END_WORKDAY;
        }
        if (text.matches("^(?:开始|现在开始)(?:本轮)?休息[。！!,.，]?$")) {
            return VoiceActionType.START_WORKDAY_REST;
        }
        if (text.matches("^(?:稍后|推迟|延后)(?:十|10)分钟(?:再)?休息[。！!,.，]?$")) {
            return VoiceActionType.SNOOZE_WORKDAY_REST;
        }
        if (text.matches("^(?:今天)?(?:跳过|不再)(?:后续)?休息提醒[。！!,.，]?$")) {
            return VoiceActionType.SKIP_WORKDAY_REST_FOR_DAY;
        }
        return null;
    }
    private ActionResult proposeNotificationResponse(UUID deviceId, UUID conversationId, UUID turnId, String text) {
        if (notificationService == null || conversationService == null) return null;
        NotificationResponseAction action = null;
        Integer minutes = null;
        Matcher snooze = SNOOZE_MINUTES.matcher(text);
        if (snooze.find() && (text.contains("通知") || text.contains("这个"))) {
            action = NotificationResponseAction.SNOOZE;
            minutes = Integer.parseInt(snooze.group(1));
        } else if (text.matches("^(?:知道了|已知晓|我知道了)[。！!,.，]?$")) {
            action = NotificationResponseAction.ACKNOWLEDGE;
        } else if (text.matches("^(?:完成了|已完成|标记完成|办完了)[。！!,.，]?$")) {
            action = NotificationResponseAction.COMPLETE;
        }
        if (action == null) return null;
        UUID roleId = conversationService.roleId(conversationId);
        UUID notificationId = notificationService.latestActionable(deviceId, roleId, action,
                conversationService.voiceTopicBoundary(conversationId));
        if (notificationId == null) {
            return new ActionResult(action == NotificationResponseAction.ACKNOWLEDGE ? "好的。"
                    : "我没找到刚才播过且支持这个操作的通知。请说清楚是通知、普通提醒还是休息提醒。", true);
        }
        VoiceActionType type = switch (action) {
            case ACKNOWLEDGE -> VoiceActionType.ACKNOWLEDGE_NOTIFICATION;
            case SNOOZE -> VoiceActionType.SNOOZE_NOTIFICATION;
            case COMPLETE -> VoiceActionType.COMPLETE_NOTIFICATION;
        };
        VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(
                deviceId, conversationId, turnId,
                VoiceActionDraft.notificationResponse(type, notificationId, minutes));
        return new ActionResult(proposalService.restatement(proposal), true);
    }
    private boolean isMuteLastTopic(String text) {
        return text.matches("^(?:别再提这个(?:话题)?(?:了)?|不要再提这个(?:话题)?(?:了)?|别提这个了|别再主动提这个(?:话题)?(?:了)?)[。！!,.，]?$");
    }
    private String statusReply(VoiceActionProposalService.ProposalSnapshot proposal) {
        return proposal.status() == VoiceActionStatus.EXECUTING ? "操作正在执行。" : "这项操作没有执行。";
    }

    public record ActionResult(String reply, boolean handled) { }
}
