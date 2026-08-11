package com.kj.stackchan.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalNotificationService {

    private static final int DEFAULT_EXPIRES_SECONDS = 86_400;
    private static final int MIN_EXPIRES_SECONDS = 60;
    private static final int MAX_EXPIRES_SECONDS = 86_400;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_INCOMPLETE = 100;
    private static final EnumSet<ReminderStatus> INCOMPLETE = EnumSet.of(
            ReminderStatus.PENDING, ReminderStatus.DISPATCHED
    );

    private final NotificationIntegrationRepository integrationRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationRateLimiter rateLimiter;
    private final Clock clock;

    public ExternalNotificationService(
            NotificationIntegrationRepository integrationRepository,
            ReminderRepository reminderRepository,
            NotificationRateLimiter rateLimiter,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.reminderRepository = reminderRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public CreateResult create(
            NotificationIntegrationPrincipal principal,
            String idempotencyKey,
            String content,
            Integer expiresInSeconds
    ) {
        ValidatedNotification validated = validate(idempotencyKey, content, expiresInSeconds);
        NotificationIntegrationEntity integration = integrationRepository.findByIdForUpdate(principal.integrationId())
                .orElseThrow(this::notFound);
        if (!integration.isEnabled() || !integration.getDeviceId().equals(principal.deviceId())) {
            throw new NotificationApiException(
                    HttpStatus.FORBIDDEN, "notification_integration_disabled", "通知集成已停用或目标已改变。"
            );
        }
        if (!rateLimiter.tryAcquire(integration.getId())) {
            throw new NotificationApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "notification_rate_limited", "通知请求过于频繁。"
            );
        }

        String contentHash = NotificationIntegrationService.hash(validated.content());
        var existing = reminderRepository.findByNotificationIntegrationIdAndIdempotencyKey(
                integration.getId(), validated.idempotencyKey()
        );
        if (existing.isPresent()) {
            if (!contentHash.equals(existing.get().getIdempotencyContentHash())) {
                throw new NotificationApiException(
                        HttpStatus.CONFLICT, "notification_idempotency_conflict", "幂等键已用于其他通知正文。"
                );
            }
            return new CreateResult(publicSnapshot(existing.get()), true);
        }

        if (reminderRepository.countByNotificationIntegrationIdAndStatusIn(integration.getId(), INCOMPLETE)
                >= MAX_INCOMPLETE) {
            throw new NotificationApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "notification_queue_full", "通知队列已达到上限。"
            );
        }

        Instant now = clock.instant();
        ReminderEntity reminder = new ReminderEntity(
                integration.getDeviceId(), validated.content(), now, "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.EXTERNAL, now
        );
        reminder.assignExternalMetadata(
                integration.getId(), validated.idempotencyKey(), contentHash,
                now.plusSeconds(validated.expiresInSeconds()), now
        );
        return new CreateResult(publicSnapshot(reminderRepository.save(reminder)), false);
    }

    @Transactional
    public CreateResult createAdminTest(UUID integrationId, String content) {
        NotificationIntegrationEntity integration = integrationRepository.findById(integrationId)
                .orElseThrow(this::notFound);
        return create(
                new NotificationIntegrationPrincipal(integration.getId(), integration.getDeviceId(), integration.getName()),
                "admin-test-" + UUID.randomUUID(), content, DEFAULT_EXPIRES_SECONDS
        );
    }

    @Transactional(readOnly = true)
    public PublicNotificationSnapshot get(NotificationIntegrationPrincipal principal, UUID notificationId) {
        return publicSnapshot(reminderRepository.findByIdAndNotificationIntegrationId(
                notificationId, principal.integrationId()
        ).filter(reminder -> reminder.getSource() == ReminderSource.EXTERNAL).orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public AdminNotificationPage adminList(
            UUID integrationId,
            ReminderStatus status,
            int from,
            int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int pageNumber = Math.max(from, 0) / safeLimit;
        Specification<ReminderEntity> specification = (root, query, builder) ->
                builder.equal(root.get("source"), ReminderSource.EXTERNAL);
        if (integrationId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("notificationIntegrationId"), integrationId));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        Page<ReminderEntity> page = reminderRepository.findAll(
                specification,
                PageRequest.of(pageNumber, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        );
        return new AdminNotificationPage(page.getContent().stream().map(this::adminSnapshot).toList(),
                page.getTotalElements());
    }

    @Transactional
    public void deleteAdmin(UUID notificationId) {
        ReminderEntity notification = reminderRepository.findByIdAndSourceForUpdate(
                notificationId, ReminderSource.EXTERNAL
        ).orElseThrow(this::notFound);
        if (notification.getStatus() == ReminderStatus.DISPATCHED) {
            throw new NotificationApiException(
                    HttpStatus.CONFLICT,
                    "notification_delivery_in_progress",
                    "通知正在播报，请等待设备确认后重试。"
            );
        }
        reminderRepository.delete(notification);
    }

    private ValidatedNotification validate(String idempotencyKey, String content, Integer expiresInSeconds) {
        String safeKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (safeKey.isBlank() || safeKey.length() > 128 || safeKey.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Idempotency-Key 无效。");
        }
        String safeContent = content == null ? "" : content.trim();
        if (safeContent.isBlank() || safeContent.length() > MAX_CONTENT_LENGTH) {
            throw invalid("通知正文必须为 1–500 字。");
        }
        int safeExpires = expiresInSeconds == null ? DEFAULT_EXPIRES_SECONDS : expiresInSeconds;
        if (safeExpires < MIN_EXPIRES_SECONDS || safeExpires > MAX_EXPIRES_SECONDS) {
            throw invalid("通知过期时间必须为 60–86400 秒。");
        }
        return new ValidatedNotification(safeKey, safeContent, safeExpires);
    }

    private PublicNotificationSnapshot publicSnapshot(ReminderEntity reminder) {
        return new PublicNotificationSnapshot(
                reminder.getId(), reminder.getStatus(), reminder.getAttemptCount(), reminder.getFailureCode(),
                reminder.getCreatedAt(), reminder.getUpdatedAt(), reminder.getExpiresAt(),
                reminder.getStatus() == ReminderStatus.DELIVERED ? reminder.getLastCompletedAt() : null
        );
    }

    private AdminNotificationSnapshot adminSnapshot(ReminderEntity reminder) {
        return new AdminNotificationSnapshot(
                reminder.getId(), reminder.getNotificationIntegrationId(), reminder.getDeviceId(),
                reminder.getContent(), reminder.getStatus(), reminder.getAttemptCount(), reminder.getFailureCode(),
                reminder.getCreatedAt(), reminder.getUpdatedAt(), reminder.getExpiresAt(),
                reminder.getStatus() == ReminderStatus.DELIVERED ? reminder.getLastCompletedAt() : null
        );
    }

    private NotificationApiException invalid(String message) {
        return new NotificationApiException(HttpStatus.BAD_REQUEST, "notification_invalid_request", message);
    }

    private NotificationApiException notFound() {
        return new NotificationApiException(
                HttpStatus.NOT_FOUND, "notification_not_found", "未找到通知或通知集成。"
        );
    }

    private record ValidatedNotification(String idempotencyKey, String content, int expiresInSeconds) { }

    public record CreateResult(PublicNotificationSnapshot notification, boolean replayed) { }

    public record PublicNotificationSnapshot(
            UUID id,
            ReminderStatus status,
            int attemptCount,
            String failureCode,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            Instant deliveredAt
    ) { }

    public record AdminNotificationSnapshot(
            UUID id,
            UUID integrationId,
            UUID deviceId,
            String content,
            ReminderStatus status,
            int attemptCount,
            String failureCode,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            Instant deliveredAt
    ) { }

    public record AdminNotificationPage(java.util.List<AdminNotificationSnapshot> list, long total) { }
}
