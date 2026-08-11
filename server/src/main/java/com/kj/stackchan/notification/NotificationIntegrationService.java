package com.kj.stackchan.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationIntegrationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration MAX_TOKEN_LIFETIME = Duration.ofDays(365);

    private final NotificationIntegrationRepository integrationRepository;
    private final NotificationIntegrationTokenRepository tokenRepository;
    private final DeviceRepository deviceRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationRateLimiter rateLimiter;
    private final Clock clock;

    public NotificationIntegrationService(
            NotificationIntegrationRepository integrationRepository,
            NotificationIntegrationTokenRepository tokenRepository,
            DeviceRepository deviceRepository,
            ReminderRepository reminderRepository,
            NotificationRateLimiter rateLimiter,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.tokenRepository = tokenRepository;
        this.deviceRepository = deviceRepository;
        this.reminderRepository = reminderRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IntegrationSnapshot> list() {
        return integrationRepository.findAllByOrderByCreatedAtDesc().stream().map(this::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public IntegrationSnapshot get(UUID id) {
        return snapshot(findIntegration(id));
    }

    @Transactional
    public IntegrationSnapshot create(IntegrationCommand command) {
        ValidatedIntegration validated = validate(command);
        return snapshot(integrationRepository.save(new NotificationIntegrationEntity(
                validated.name(), validated.deviceId(), validated.enabled(), clock.instant()
        )));
    }

    @Transactional
    public IntegrationSnapshot update(UUID id, IntegrationCommand command) {
        ValidatedIntegration validated = validate(command);
        NotificationIntegrationEntity integration = findIntegration(id);
        integration.update(validated.name(), validated.deviceId(), validated.enabled(), clock.instant());
        return snapshot(integration);
    }

    @Transactional
    public IssuedToken issueToken(UUID integrationId, Instant expiresAt) {
        NotificationIntegrationEntity integration = findIntegration(integrationId);
        Instant now = clock.instant();
        if (expiresAt != null && (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plus(MAX_TOKEN_LIFETIME)))) {
            throw invalid("令牌到期时间必须在未来一年内。");
        }
        byte[] entropy = new byte[32];
        SECURE_RANDOM.nextBytes(entropy);
        String rawToken = "scn_" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        NotificationIntegrationTokenEntity token = tokenRepository.save(new NotificationIntegrationTokenEntity(
                integration.getId(), hash(rawToken), expiresAt, now
        ));
        return new IssuedToken(rawToken, tokenSnapshot(token));
    }

    @Transactional
    public void revokeToken(UUID integrationId, UUID tokenId) {
        findIntegration(integrationId);
        NotificationIntegrationTokenEntity token = tokenRepository.findByIdAndIntegrationId(tokenId, integrationId)
                .orElseThrow(() -> notFound("未找到通知令牌。"));
        token.revoke(clock.instant());
    }

    @Transactional
    public void delete(UUID integrationId) {
        NotificationIntegrationEntity integration = integrationRepository.findByIdForUpdate(integrationId)
                .orElseThrow(() -> notFound("未找到通知集成。"));
        var notifications = reminderRepository.findAllByNotificationIntegrationIdForUpdate(integrationId);
        if (notifications.stream().anyMatch(notification -> notification.getStatus() == ReminderStatus.DISPATCHED)) {
            throw new NotificationApiException(
                    HttpStatus.CONFLICT,
                    "notification_delivery_in_progress",
                    "集成仍有正在播报的通知，请等待设备确认后重试。"
            );
        }
        reminderRepository.deleteAll(notifications);
        reminderRepository.flush();
        integrationRepository.delete(integration);
        rateLimiter.forget(integrationId);
    }

    @Transactional
    public NotificationIntegrationPrincipal authenticate(String rawToken) {
        if (rawToken == null || rawToken.length() < 20 || rawToken.length() > 256) {
            throw authenticationFailed();
        }
        NotificationIntegrationTokenEntity token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(this::authenticationFailed);
        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            throw authenticationFailed();
        }
        NotificationIntegrationEntity integration = integrationRepository.findById(token.getIntegrationId())
                .orElseThrow(this::authenticationFailed);
        if (!integration.isEnabled()) {
            throw new NotificationApiException(
                    HttpStatus.FORBIDDEN, "notification_integration_disabled", "通知集成已停用。"
            );
        }
        token.markUsed(now);
        return new NotificationIntegrationPrincipal(
                integration.getId(), integration.getDeviceId(), integration.getName()
        );
    }

    private ValidatedIntegration validate(IntegrationCommand command) {
        if (command == null || command.deviceId() == null || !deviceRepository.existsById(command.deviceId())) {
            throw invalid("目标设备无效。");
        }
        String name = command.name() == null ? "" : command.name().trim();
        if (name.isBlank() || name.length() > 120) {
            throw invalid("集成名称无效。");
        }
        return new ValidatedIntegration(name, command.deviceId(), command.enabled());
    }

    private NotificationIntegrationEntity findIntegration(UUID id) {
        return integrationRepository.findById(id)
                .orElseThrow(() -> notFound("未找到通知集成。"));
    }

    private IntegrationSnapshot snapshot(NotificationIntegrationEntity integration) {
        List<TokenSnapshot> tokens = tokenRepository
                .findAllByIntegrationIdOrderByCreatedAtDesc(integration.getId()).stream()
                .map(this::tokenSnapshot)
                .toList();
        return new IntegrationSnapshot(
                integration.getId(), integration.getName(), integration.getDeviceId(), integration.isEnabled(),
                tokens, integration.getCreatedAt(), integration.getUpdatedAt()
        );
    }

    private TokenSnapshot tokenSnapshot(NotificationIntegrationTokenEntity token) {
        return new TokenSnapshot(
                token.getId(), token.getExpiresAt(), token.getRevokedAt(), token.getLastUsedAt(), token.getCreatedAt()
        );
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private NotificationApiException authenticationFailed() {
        return new NotificationApiException(
                HttpStatus.UNAUTHORIZED, "notification_authentication_failed", "通知令牌无效或已过期。"
        );
    }

    private NotificationApiException invalid(String message) {
        return new NotificationApiException(HttpStatus.BAD_REQUEST, "notification_invalid_request", message);
    }

    private NotificationApiException notFound(String message) {
        return new NotificationApiException(HttpStatus.NOT_FOUND, "notification_not_found", message);
    }

    private record ValidatedIntegration(String name, UUID deviceId, boolean enabled) { }

    public record IntegrationCommand(String name, UUID deviceId, boolean enabled) { }

    public record IntegrationSnapshot(
            UUID id,
            String name,
            UUID deviceId,
            boolean enabled,
            List<TokenSnapshot> tokens,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record TokenSnapshot(
            UUID id,
            Instant expiresAt,
            Instant revokedAt,
            Instant lastUsedAt,
            Instant createdAt
    ) { }

    public record IssuedToken(String token, TokenSnapshot metadata) { }
}
