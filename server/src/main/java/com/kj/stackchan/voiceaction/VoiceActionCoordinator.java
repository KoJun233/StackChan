package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.memory.MemoryCategory;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderService;
import org.springframework.stereotype.Service;

@Service
public class VoiceActionCoordinator {
    private static final Pattern VOLUME = Pattern.compile("音量(?:调到|设置为|设为)\\s*(\\d{1,3})\\s*%?");
    private static final Pattern DND_MINUTES = Pattern.compile("(?:安静|免打扰)(?:到|持续)\\s*(\\d{1,4})\\s*分钟");
    private static final Pattern REMINDER_MINUTES = Pattern.compile("提醒我\\s*(.+?)\\s*(\\d{1,5})\\s*分钟后");
    private static final Pattern SNOOZE_MINUTES = Pattern.compile("(?:稍后|推迟|延后)\\s*(\\d{1,4})\\s*分钟");

    private final VoiceActionProposalService proposalService;
    private final ReminderService reminderService;
    private final LongTermMemoryService memoryService;
    private final InteractionSettingsService settingsService;
    private final Clock clock;
    private final VoiceActionProposalOrchestrator proposalOrchestrator;

    public VoiceActionCoordinator(VoiceActionProposalService proposalService, ReminderService reminderService,
                                  LongTermMemoryService memoryService, InteractionSettingsService settingsService,
                                  Clock clock, VoiceActionProposalOrchestrator proposalOrchestrator) {
        this.proposalService = proposalService;
        this.reminderService = reminderService;
        this.memoryService = memoryService;
        this.settingsService = settingsService;
        this.clock = clock;
        this.proposalOrchestrator = proposalOrchestrator;
    }

    public ActionResult handle(UUID deviceId, UUID conversationId, UUID turnId, String transcript) {
        String text = transcript == null ? "" : transcript.trim();
        if (text.isBlank()) return null;
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

        Matcher snooze = SNOOZE_MINUTES.matcher(text);
        if (snooze.find() && text.contains("提醒")) {
            int minutes = Integer.parseInt(snooze.group(1));
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SNOOZE_NEXT_REMINDER, true, null, null, null, null, null,
                            null, minutes, null, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        if ((text.contains("跳过") || text.contains("略过")) && text.contains("提醒")) {
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SKIP_NEXT_REMINDER, true, null, null, null, null, null,
                            null, null, null, null, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }

        Matcher volume = VOLUME.matcher(text);
        if (volume.find()) {
            int value = Integer.parseInt(volume.group(1));
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SET_VOLUME, true, null, null, null, null, null, null, null, null, value, null));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        Matcher dnd = DND_MINUTES.matcher(text);
        if (dnd.find()) {
            int minutes = Integer.parseInt(dnd.group(1));
            Instant until = clock.instant().plus(Duration.ofMinutes(minutes));
            VoiceActionProposalService.ProposalSnapshot proposal = proposalService.propose(deviceId, conversationId, turnId,
                    new VoiceActionDraft(VoiceActionType.SET_TEMPORARY_DND, true, null, null, null, null, null, null,
                            minutes, until, null, null));
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
                            null, null, null, null, null, null, null, MemoryCategory.USER_PROFILE.name()));
            return new ActionResult(proposalService.restatement(proposal), true);
        }
        if (text.contains("下一条提醒") || text.contains("下一个提醒")) {
            ReminderService.ReminderSnapshot next = reminderService.nextPending(deviceId);
            return new ActionResult(next == null ? "当前没有待处理提醒。" : "下一条提醒是：" + next.content() + "，时间为 " + next.scheduledAt() + "。", true);
        }
        if (text.contains("待确认记忆") || text.contains("待确认的记忆")) {
            return new ActionResult("当前有 " + memoryService.pendingVisibleCount(deviceId) + " 条待确认记忆。", true);
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
    private boolean isCancel(String text) { return text.matches("^(取消|不用了|不要|算了)[。！!,.，]?$"); }
    private boolean isExplicitAction(String text) {
        return text.contains("提醒我") || text.contains("稍后提醒") || text.contains("跳过下一次")
                || text.contains("音量调到") || text.contains("安静到") || text.startsWith("记住")
                || text.startsWith("请记住");
    }
    private String statusReply(VoiceActionProposalService.ProposalSnapshot proposal) {
        return proposal.status() == VoiceActionStatus.EXECUTING ? "操作正在执行。" : "这项操作没有执行。";
    }

    public record ActionResult(String reply, boolean handled) { }
}
