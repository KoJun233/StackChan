package com.kj.stackchan.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractiveNotificationService {
    private static final Duration RESPONSE_WINDOW = Duration.ofHours(24);

    private final ReminderRepository reminderRepository;
    private final NotificationResponseRepository responseRepository;
    private final Clock clock;

    public InteractiveNotificationService(
            ReminderRepository reminderRepository,
            NotificationResponseRepository responseRepository,
            Clock clock
    ) {
        this.reminderRepository = reminderRepository;
        this.responseRepository = responseRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UUID latestActionable(UUID deviceId, UUID roleId, NotificationResponseAction action) {
        return latestActionable(deviceId, roleId, action, null);
    }

    @Transactional(readOnly = true)
    public UUID latestActionable(UUID deviceId, UUID roleId, NotificationResponseAction action, Instant topicBoundary) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofMinutes(30));
        if (topicBoundary != null && topicBoundary.isAfter(cutoff)) cutoff = topicBoundary;
        var recent = reminderRepository.findRecentCompletedDeliveries(deviceId, roleId, cutoff, now,
                org.springframework.data.domain.PageRequest.of(0, 2));
        if (recent.isEmpty()) return null;
        var latest = recent.getFirst();
        // Never skip a newer ordinary reminder, unsupported notification or ambiguous digest member.
        if (recent.size() > 1 && latest.getLastCompletedAt().equals(recent.get(1).getLastCompletedAt())) return null;
        if (latest.getSource() != ReminderSource.EXTERNAL || latest.getStatus() != ReminderStatus.DELIVERED
                || !latest.getResponseActions().contains(action) || hasTerminalResponse(latest.getId())) return null;
        return latest.getId();
    }

    @Transactional
    public ResponseSnapshot respond(
            UUID notificationId,
            UUID deviceId,
            UUID roleId,
            NotificationResponseAction action,
            Integer snoozeMinutes
    ) {
        ReminderEntity notification = reminderRepository.findByIdAndSourceForUpdate(notificationId, ReminderSource.EXTERNAL)
                .orElseThrow(this::notFound);
        if (!notification.getDeviceId().equals(deviceId) || !notification.getRoleId().equals(roleId)) {
            throw notFound();
        }
        return respondLocked(notification, action, snoozeMinutes);
    }

    @Transactional(readOnly = true)
    public String descriptionForVoice(UUID notificationId, UUID deviceId, UUID roleId, NotificationResponseAction action) {
        var notification = reminderRepository.findByIdAndDeviceId(notificationId, deviceId).orElseThrow(this::notFound);
        Instant now = clock.instant();
        if (!notification.getRoleId().equals(roleId) || notification.getSource() != ReminderSource.EXTERNAL) throw notFound();
        if (notification.getStatus() != ReminderStatus.DELIVERED || notification.getLastCompletedAt() == null
                || notification.getLastCompletedAt().isAfter(now)
                || notification.getLastCompletedAt().isBefore(now.minus(RESPONSE_WINDOW))
                || !notification.getResponseActions().contains(action) || hasTerminalResponse(notificationId)) {
            throw invalid("该通知当前不能回应，请重新指定对象。");
        }
        return notification.getContent();
    }

    @Transactional
    public ResponseSnapshot respondAdmin(UUID notificationId, NotificationResponseAction action, Integer snoozeMinutes) {
        ReminderEntity notification = reminderRepository.findByIdAndSourceForUpdate(notificationId, ReminderSource.EXTERNAL)
                .orElseThrow(this::notFound);
        return respondLocked(notification, action, snoozeMinutes);
    }

    @Transactional(readOnly = true)
    public ResponseSnapshot latestResponse(UUID notificationId) {
        return responseRepository.findFirstByNotificationIdOrderByCreatedAtDescIdDesc(notificationId)
                .map(this::snapshot)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ResponseSnapshot> responseHistory(UUID notificationId) {
        return responseRepository.findAllByNotificationIdOrderByCreatedAtAscIdAsc(notificationId)
                .stream().map(this::snapshot).toList();
    }

    private ResponseSnapshot respondLocked(
            ReminderEntity notification,
            NotificationResponseAction action,
            Integer snoozeMinutes
    ) {
        if (action == null || !notification.getResponseActions().contains(action)) {
            throw invalid("该通知未授权此回执动作。");
        }
        if (notification.getStatus() != ReminderStatus.DELIVERED || notification.getLastCompletedAt() == null) {
            throw new NotificationApiException(
                    HttpStatus.CONFLICT, "notification_response_unavailable", "通知尚未完成播报或当前不可回应。"
            );
        }
        Instant now = clock.instant();
        if (notification.getLastCompletedAt().isBefore(now.minus(RESPONSE_WINDOW))) {
            throw new NotificationApiException(
                    HttpStatus.CONFLICT, "notification_response_expired", "通知回应窗口已结束。"
            );
        }
        NotificationResponseEntity latest = responseRepository
                .findFirstByNotificationIdOrderByCreatedAtDescIdDesc(notification.getId()).orElse(null);
        if (latest != null && latest.getAction() != NotificationResponseAction.SNOOZE) {
            return snapshot(latest);
        }
        Integer safeMinutes = null;
        if (action == NotificationResponseAction.SNOOZE) {
            if (snoozeMinutes == null || snoozeMinutes < 1 || snoozeMinutes > 1440) {
                throw invalid("稍后提醒时间必须为 1–1440 分钟。");
            }
            safeMinutes = snoozeMinutes;
            Instant scheduledAt = now.plus(Duration.ofMinutes(safeMinutes));
            notification.snoozeExternalUntil(scheduledAt, scheduledAt.plus(RESPONSE_WINDOW), now);
        } else if (snoozeMinutes != null) {
            throw invalid("只有稍后提醒可以提供分钟数。");
        }
        NotificationResponseEntity response = responseRepository.save(new NotificationResponseEntity(
                notification.getId(), notification.getNotificationIntegrationId(), action, safeMinutes, now
        ));
        return snapshot(response);
    }

    private boolean hasTerminalResponse(UUID notificationId) {
        return responseRepository.findFirstByNotificationIdOrderByCreatedAtDescIdDesc(notificationId)
                .map(response -> response.getAction() != NotificationResponseAction.SNOOZE)
                .orElse(false);
    }

    private ResponseSnapshot snapshot(NotificationResponseEntity response) {
        return new ResponseSnapshot(response.getAction(), response.getSnoozeMinutes(), response.getCreatedAt());
    }

    private NotificationApiException invalid(String message) {
        return new NotificationApiException(HttpStatus.BAD_REQUEST, "notification_invalid_request", message);
    }

    private NotificationApiException notFound() {
        return new NotificationApiException(HttpStatus.NOT_FOUND, "notification_not_found", "未找到通知。");
    }

    public record ResponseSnapshot(NotificationResponseAction action, Integer snoozeMinutes, Instant respondedAt) { }
}
