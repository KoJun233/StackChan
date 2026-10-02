package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.interaction.DeviceQuietTodayService;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.voiceaction.*;
import com.kj.stackchan.workday.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DeviceUiService {
    private final ObjectProvider<DeviceConnectionRegistry> registries;
    private final VoiceActionProposalService proposals;
    private final DeviceVoiceConversationService conversations;
    private final CompanionRoleService roles;
    private final InteractionSettingsService interactions;
    private final DeviceInteractionSettingsCoordinator interactionCoordinator;
    private final DeviceQuietTodayService quiet;
    private final WorkdayRuntimeService runtime;
    private final WorkdayCompanionService workday;
    private final Clock clock;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public DeviceUiService(ObjectProvider<DeviceConnectionRegistry> registries, VoiceActionProposalService proposals,
                           DeviceVoiceConversationService conversations, CompanionRoleService roles,
                           InteractionSettingsService interactions, DeviceInteractionSettingsCoordinator interactionCoordinator,
                           DeviceQuietTodayService quiet, WorkdayRuntimeService runtime, WorkdayCompanionService workday,
                           Clock clock, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.registries = registries; this.proposals = proposals; this.conversations = conversations;
        this.roles = roles; this.interactions = interactions; this.interactionCoordinator = interactionCoordinator;
        this.quiet = quiet; this.runtime = runtime; this.workday = workday; this.clock = clock; this.mapper = mapper;
    }

    public boolean presentAndAwait(UUID deviceId, UUID conversationId) {
        DeviceConnectionRegistry registry = registries.getIfAvailable();
        if (registry == null || !registry.supportsDeviceUi(deviceId)) return false;
        var proposal = proposals.latestPending(deviceId, conversationId);
        if (proposal == null || !proposal.confirmationRequired()) return false;
        try {
            // Card availability alone does not prove visibility. Only a scoped HTTP receipt earns short speech.
            return registry.offerConfirmation(deviceId, proposal.id()).get(1200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            return false;
        }
    }

    public boolean pendingVisible(UUID deviceId) {
        DeviceConnectionRegistry registry = registries.getIfAvailable();
        if (registry == null || !registry.confirmationShown(deviceId)) return false;
        UUID id = registry.confirmationId(deviceId);
        try { return id != null && proposals.screen(id, deviceId).proposal().status() == VoiceActionStatus.PENDING; }
        catch (VoiceActionException exception) { return false; }
    }

    public Card card(UUID deviceId, UUID proposalId) {
        requireOffer(deviceId, proposalId);
        try { return card(proposals.screen(proposalId, deviceId), false); }
        catch (VoiceActionException exception) { throw unavailable(); }
    }

    public Card act(UUID deviceId, UUID proposalId, String action) {
        requireOffer(deviceId, proposalId);
        try {
            var scoped = proposals.screen(proposalId, deviceId);
            // Confirm is accepted only after the exact card was rendered on this connection.
            if ("CONFIRM".equals(action) && !registry().confirmationShown(deviceId))
                throw new DeviceUiException(HttpStatus.CONFLICT, "confirmation_not_shown");
            var result = "CONFIRM".equals(action)
                    ? proposals.confirm(proposalId, deviceId, scoped.conversationId())
                    : proposals.cancel(proposalId, deviceId, scoped.conversationId());
            return card(new VoiceActionProposalService.ScreenProposal(result, scoped.conversationId(), scoped.roleId(),
                    scoped.sourceTurnId(), scoped.zoneId(), scoped.roleName()), true);
        } catch (VoiceActionException exception) { throw unavailable(); }
    }

    public void shown(UUID deviceId, UUID proposalId) {
        Card card = card(deviceId, proposalId);
        if (!"PENDING".equals(card.status()) || !registry().acknowledgeConfirmationShown(deviceId, proposalId))
            throw new DeviceUiException(HttpStatus.CONFLICT, "confirmation_unavailable");
    }

    public State state(UUID deviceId) {
        var settings = interactions.resolve(deviceId);
        var active = roles.getActive(deviceId);
        var work = runtime.get(deviceId);
        var available = roles.list().stream().filter(role -> role.archivedAt() == null).toList();
        DeviceConnectionRegistry registry = registries.getIfAvailable();
        UUID pending = registry == null ? null : registry.confirmationId(deviceId);
        if (pending != null) {
            try {
                if (proposals.screen(pending, deviceId).proposal().status() != VoiceActionStatus.PENDING) pending = null;
            } catch (VoiceActionException exception) { pending = null; }
        }
        var options = new java.util.ArrayList<RoleOption>();
        int optionBytes = 0;
        for (var role : available) {
            var option = new RoleOption(role.id(), role.name());
            int bytes;
            try { bytes = mapper.writeValueAsBytes(option).length + 1; }
            catch (java.io.IOException exception) { throw new DeviceUiException(HttpStatus.SERVICE_UNAVAILABLE, "device_ui_unavailable"); }
            if (options.size() >= 32 || optionBytes + bytes > 5500) break;
            optionBytes += bytes;
            options.add(option);
        }
        return new State(1, settings.volumePercent(), settings.nightMode(), quiet.isQuiet(deviceId, clock.instant()),
                work.state().name(), work.state() == WorkdayRuntimeState.REST_PROMPTED,
                new RoleOption(active.id(), active.name()), List.copyOf(options), available.size() > options.size(), pending);
    }

    public State setting(UUID deviceId, String field, Object value) {
        if (pendingVisible(deviceId)) throw new DeviceUiException(HttpStatus.CONFLICT, "confirmation_pending");
        switch (field) {
            case "volume_percent" -> interactionCoordinator.send(interactions.setVolume(deviceId, (Integer) value));
            case "night_mode" -> interactionCoordinator.send(interactions.setNightMode(deviceId, (Boolean) value));
            case "quiet_today" -> { if ((Boolean) value) quiet.quietForToday(deviceId); else quiet.resume(deviceId); }
            case "workday_action" -> { if ("START".equals(value)) workday.start(deviceId); else workday.stop(deviceId); }
            case "rest_action" -> workday.respondToRest(deviceId, WorkdayRestAction.valueOf((String) value));
            case "role_id" -> roles.switchActive(deviceId, (UUID) value);
            default -> throw new DeviceUiException(HttpStatus.BAD_REQUEST, "invalid_ui_request");
        }
        return state(deviceId);
    }

    private Card card(VoiceActionProposalService.ScreenProposal scoped, boolean includeResult) {
        var proposal = scoped.proposal();
        String label = label(proposal.actionType());
        String title = text(proposal.title());
        if (title.isBlank()) title = label;
        String content = text(proposal.content());
        if (content.isBlank()) {
            content = switch (proposal.actionType()) {
                case SET_VOLUME -> proposal.volumePercent() + "%";
                case START_WORKDAY -> "开始当前设备的工作模式";
                case END_WORKDAY -> "结束当前设备的工作模式";
                case START_WORKDAY_REST -> "开始本轮休息";
                case SNOOZE_WORKDAY_REST -> "推迟十分钟再休息";
                case SKIP_WORKDAY_REST_FOR_DAY -> "今天跳过后续休息提醒";
                default -> label;
            };
        }
        Instant time = proposal.scheduledAt();
        if (time == null && proposal.actionType() == VoiceActionType.SET_TEMPORARY_DND) time = proposal.targetAt();
        String timeLabel = "";
        if (time != null) {
            String zone = scoped.zoneId() == null ? interactions.resolve(
                    proposalsDevice(scoped)).zoneId() : scoped.zoneId();
            timeLabel = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(zone)).format(time) + " " + zone;
        } else if (proposal.durationMinutes() != null) timeLabel = proposal.durationMinutes() + " 分钟";
        if (title.length() > 200 || content.length() > 2000)
            throw new DeviceUiException(HttpStatus.CONFLICT, "confirmation_too_large");
        long remaining = Math.max(0, Math.min(120, Duration.between(clock.instant(), proposal.expiresAt()).toSeconds()));
        return new Card(proposal.id(), label, title, content, timeLabel, text(scoped.roleName()),
                proposal.expiresAt(), (int) remaining, proposal.status().name(), includeResult ? result(proposal.status()) : "");
    }

    private UUID proposalsDevice(VoiceActionProposalService.ScreenProposal scoped) {
        return conversations.findDeviceIdByConversationId(scoped.conversationId()).orElseThrow(DeviceUiService::unavailable);
    }

    private static String text(String value) { return value == null ? "" : value; }
    private static String label(VoiceActionType type) {
        return switch (type) {
            case CREATE_REMINDER -> "创建提醒";
            case SNOOZE_NEXT_REMINDER -> "推迟提醒";
            case SKIP_NEXT_REMINDER -> "跳过下一次提醒";
            case SET_TEMPORARY_DND -> "临时免打扰";
            case SET_VOLUME -> "设置音量";
            case CREATE_MEMORY_SUGGESTION, CONFIRM_MEMORY -> "记住内容";
            case CREATE_FOLLOW_UP -> "安排一次关心";
            case SWITCH_ROLE -> "切换伙伴";
            case ACKNOWLEDGE_NOTIFICATION -> "标记通知已知晓";
            case COMPLETE_NOTIFICATION -> "回报通知已完成";
            case SNOOZE_NOTIFICATION -> "稍后播报通知";
            case START_WORKDAY -> "开始工作";
            case END_WORKDAY -> "结束工作";
            case START_WORKDAY_REST -> "开始休息";
            case SNOOZE_WORKDAY_REST -> "推迟休息";
            case SKIP_WORKDAY_REST_FOR_DAY -> "跳过今日休息提醒";
            case CREATE_PERSONAL_TASK -> "新增待办";
            case COMPLETE_PERSONAL_TASK -> "完成待办";
        };
    }
    private static String result(VoiceActionStatus status) {
        return switch (status) {
            case EXECUTED -> "已执行";
            case CANCELLED -> "已取消";
            case EXPIRED -> "已过期，请重新说";
            case FAILED -> "执行失败，内容或状态可能已变化，请重新说";
            case EXECUTING -> "正在执行";
            case PENDING -> "等待确认";
        };
    }
    private DeviceConnectionRegistry registry() {
        var registry = registries.getIfAvailable();
        if (registry == null) throw new DeviceUiException(HttpStatus.SERVICE_UNAVAILABLE, "device_ui_offline");
        return registry;
    }
    private void requireOffer(UUID deviceId, UUID proposalId) {
        if (!proposalId.equals(registry().confirmationId(deviceId))) throw unavailable();
    }
    private static DeviceUiException unavailable() {
        return new DeviceUiException(HttpStatus.NOT_FOUND, "confirmation_unavailable");
    }
    public record RoleOption(UUID id, String name) {}
    public record State(int version, int volume_percent, boolean night_mode, boolean quiet_today, String workday_state,
                        boolean rest_pending, RoleOption role, List<RoleOption> roles, boolean roles_truncated,
                        UUID pending_confirmation_id) {}
    public record Card(UUID proposal_id, String action_label, String title, String content, String time_label,
                       String role_name,
                       @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
                       Instant expires_at, int expires_in_seconds, String status, String result_message) {}
}
