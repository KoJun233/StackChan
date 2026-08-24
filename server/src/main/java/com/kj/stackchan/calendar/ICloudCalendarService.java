package com.kj.stackchan.calendar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.llm.SecretCipher;
import com.kj.stackchan.workday.WorkdaySettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ICloudCalendarService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration QUERY_WINDOW = Duration.ofDays(7);
    private static final int MAX_AGGREGATE_EVENTS = 1000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ICloudCalendarConnectionRepository connectionRepository;
    private final ICloudCalendarRepository calendarRepository;
    private final WorkdayCalendarEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final WorkdaySettingsService workdaySettingsService;
    private final SecretCipher secretCipher;
    private final ICloudCalDavClient calDavClient;
    private final Clock clock;

    public ICloudCalendarService(
            ICloudCalendarConnectionRepository connectionRepository,
            ICloudCalendarRepository calendarRepository,
            WorkdayCalendarEventRepository eventRepository,
            DeviceRepository deviceRepository,
            WorkdaySettingsService workdaySettingsService,
            SecretCipher secretCipher,
            ICloudCalDavClient calDavClient,
            Clock clock
    ) {
        this.connectionRepository = connectionRepository;
        this.calendarRepository = calendarRepository;
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.workdaySettingsService = workdaySettingsService;
        this.secretCipher = secretCipher;
        this.calDavClient = calDavClient;
        this.clock = clock;
    }

    @Transactional
    public ConnectionSnapshot get(UUID deviceId) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        eventRepository.deleteByExpiresAtBefore(now);
        return connectionRepository.findById(deviceId)
                .map(connection -> snapshot(connection, now))
                .orElseGet(() -> ConnectionSnapshot.empty(deviceId));
    }

    public ConnectionTestSnapshot test(UUID deviceId, ConnectionCommand command) {
        validateDevice(deviceId);
        ResolvedCredentials credentials = resolveCredentials(deviceId, command);
        ICloudCalDavClient.DiscoveryResult discovery = calDavClient.discover(
                credentials.accountEmail(), credentials.appSpecificPassword()
        );
        return new ConnectionTestSnapshot(
                true,
                maskAccount(credentials.accountEmail()),
                discovery.calendars().size(),
                discovery.calendars().stream().map(ICloudCalDavClient.DiscoveredCalendar::displayName).toList()
        );
    }

    @Transactional(noRollbackFor = ICloudCalendarUnavailableException.class)
    public ConnectionSnapshot connect(UUID deviceId, ConnectionCommand command) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        ICloudCalendarConnectionEntity existing = connectionRepository.findById(deviceId).orElse(null);
        ResolvedCredentials credentials = resolveCredentials(existing, command);
        try {
            ICloudCalDavClient.DiscoveryResult discovery = calDavClient.discover(
                    credentials.accountEmail(), credentials.appSpecificPassword()
            );
            SecretCipher.EncryptedSecret encrypted = credentials.newPassword()
                    ? secretCipher.encrypt(credentials.appSpecificPassword())
                    : new SecretCipher.EncryptedSecret(existing.getPasswordCiphertext(), existing.getPasswordIv());
            ICloudCalendarConnectionEntity connection = existing == null
                    ? new ICloudCalendarConnectionEntity(
                            deviceId, credentials.accountEmail(), encrypted.ciphertext(),
                            encrypted.initializationVector(), now
                    )
                    : existing;
            if (existing != null) {
                connection.updateCredentials(
                        credentials.accountEmail(), encrypted.ciphertext(), encrypted.initializationVector(), now
                );
            }
            connection.connected(discovery.principalUrl(), discovery.calendarHomeUrl(), now);
            connectionRepository.saveAndFlush(connection);
            reconcileCalendars(deviceId, discovery.calendars(), now);
            return snapshot(connection, now);
        } catch (ICloudCalendarUnavailableException exception) {
            if (existing != null) {
                existing.failed(exception.getFailureCode(), now);
                connectionRepository.save(existing);
            }
            throw exception;
        }
    }

    @Transactional
    public ConnectionSnapshot updateAllowed(UUID deviceId, Set<UUID> allowedCalendarIds) {
        validateDevice(deviceId);
        ICloudCalendarConnectionEntity connection = requiredConnection(deviceId);
        Set<UUID> requested = allowedCalendarIds == null ? Set.of() : Set.copyOf(allowedCalendarIds);
        List<ICloudCalendarEntity> calendars = calendarRepository.findAllByDeviceIdOrderByDisplayNameAsc(deviceId);
        Set<UUID> available = calendars.stream().map(ICloudCalendarEntity::getId).collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(requested)) {
            throw new InvalidICloudCalendarException("Allowed calendar list contains an unknown calendar");
        }
        Instant now = clock.instant();
        calendars.forEach(calendar -> calendar.setAllowed(requested.contains(calendar.getId()), now));
        calendarRepository.saveAll(calendars);
        return snapshot(connection, now);
    }

    @Transactional(noRollbackFor = ICloudCalendarUnavailableException.class)
    public ConnectionSnapshot sync(UUID deviceId) {
        validateDevice(deviceId);
        ICloudCalendarConnectionEntity connection = requiredConnection(deviceId);
        Instant now = clock.instant();
        String password = decrypt(connection);
        ZoneId zoneId = ZoneId.of(workdaySettingsService.resolve(deviceId).zoneId());
        List<ICloudCalendarEntity> allowed = calendarRepository
                .findAllByDeviceIdAndAllowedTrueOrderByDisplayNameAsc(deviceId);
        List<PendingEvent> pending = new ArrayList<>();
        try {
            for (ICloudCalendarEntity calendar : allowed) {
                List<ICloudCalDavClient.CalendarEvent> events = calDavClient.queryEvents(
                        calendar.getCalendarHref(), connection.getAccountEmail(), password,
                        now, now.plus(QUERY_WINDOW), zoneId
                );
                for (ICloudCalDavClient.CalendarEvent event : events) {
                    pending.add(new PendingEvent(calendar.getId(), event));
                    if (pending.size() > MAX_AGGREGATE_EVENTS) {
                        throw new ICloudCalendarUnavailableException(
                                ICloudCalendarFailureCode.RESPONSE_TOO_LARGE,
                                "Too many cached calendar events"
                        );
                    }
                }
            }
            eventRepository.deleteAllByDeviceId(deviceId);
            Instant expiresAt = now.plus(CACHE_TTL);
            eventRepository.saveAll(pending.stream().map(item -> toEntity(deviceId, item, now, expiresAt)).toList());
            connection.synced(now);
            connectionRepository.save(connection);
            return snapshot(connection, now);
        } catch (ICloudCalendarUnavailableException exception) {
            ICloudCalendarFailureCode failureCode = exception.getFailureCode() == ICloudCalendarFailureCode.DISCOVERY_FAILED
                    ? ICloudCalendarFailureCode.SYNC_FAILED
                    : exception.getFailureCode();
            connection.failed(failureCode, now);
            connectionRepository.save(connection);
            if (failureCode == exception.getFailureCode()) {
                throw exception;
            }
            throw new ICloudCalendarUnavailableException(failureCode, "iCloud calendar synchronization failed", exception);
        }
    }

    @Transactional
    public void disconnect(UUID deviceId) {
        validateDevice(deviceId);
        ICloudCalendarConnectionEntity connection = requiredConnection(deviceId);
        connectionRepository.delete(connection);
    }

    @Transactional
    public List<CachedEventSnapshot> cachedEvents(UUID deviceId, Instant from, Instant to) {
        validateDevice(deviceId);
        if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(QUERY_WINDOW) > 0) {
            throw new InvalidICloudCalendarException("Calendar event range is invalid");
        }
        Instant now = clock.instant();
        eventRepository.deleteByExpiresAtBefore(now);
        return eventRepository
                .findAllByDeviceIdAndExpiresAtAfterAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(
                        deviceId, now, from, to
                )
                .stream()
                .map(event -> new CachedEventSnapshot(
                        event.getStartsAt(), event.getEndsAt(), event.isAllDay(), event.isBusy(),
                        event.isPrivateEvent(), event.getTitle(), event.getLocation()
                ))
                .toList();
    }

    private void reconcileCalendars(
            UUID deviceId,
            List<ICloudCalDavClient.DiscoveredCalendar> discovered,
            Instant now
    ) {
        List<ICloudCalendarEntity> existing = calendarRepository.findAllByDeviceIdOrderByDisplayNameAsc(deviceId);
        Map<String, ICloudCalendarEntity> byKey = new HashMap<>();
        existing.forEach(calendar -> byKey.put(calendar.getCalendarKey(), calendar));
        Set<String> seen = new HashSet<>();
        for (ICloudCalDavClient.DiscoveredCalendar calendar : discovered) {
            String key = digest(calendar.href());
            seen.add(key);
            ICloudCalendarEntity entity = byKey.get(key);
            if (entity == null) {
                entity = new ICloudCalendarEntity(
                        UUID.randomUUID(), deviceId, key, calendar.href(), calendar.displayName(), now
                );
            } else {
                entity.updateDiscovery(calendar.href(), calendar.displayName(), now);
            }
            calendarRepository.save(entity);
        }
        existing.stream()
                .filter(calendar -> !seen.contains(calendar.getCalendarKey()))
                .forEach(calendarRepository::delete);
    }

    private WorkdayCalendarEventEntity toEntity(
            UUID deviceId,
            PendingEvent pending,
            Instant now,
            Instant expiresAt
    ) {
        ICloudCalDavClient.CalendarEvent event = pending.event();
        String eventKey = digest(event.uid() + "|" + event.startsAt());
        return new WorkdayCalendarEventEntity(
                UUID.randomUUID(), deviceId, pending.calendarId(), eventKey,
                event.startsAt(), event.endsAt(), event.allDay(), event.busy(), event.privateEvent(),
                event.privateEvent() ? "私人日程" : event.title(),
                event.privateEvent() ? null : event.location(), now, expiresAt
        );
    }

    private ResolvedCredentials resolveCredentials(UUID deviceId, ConnectionCommand command) {
        return resolveCredentials(connectionRepository.findById(deviceId).orElse(null), command);
    }

    private ResolvedCredentials resolveCredentials(
            ICloudCalendarConnectionEntity existing,
            ConnectionCommand command
    ) {
        if (command == null) {
            throw new InvalidICloudCalendarException("iCloud calendar credentials are required");
        }
        String accountEmail = normalizeAccountIdentifier(command.accountEmail(), existing);
        String submittedPassword = command.appSpecificPassword() == null ? "" : command.appSpecificPassword().trim();
        if (submittedPassword.length() > 256) {
            throw new InvalidICloudCalendarException("iCloud app-specific password is invalid");
        }
        if (submittedPassword.isBlank()) {
            if (existing == null) {
                throw new InvalidICloudCalendarException("iCloud app-specific password is required");
            }
            return new ResolvedCredentials(accountEmail, decrypt(existing), false);
        }
        return new ResolvedCredentials(accountEmail, submittedPassword, true);
    }

    private String normalizeAccountIdentifier(String submitted, ICloudCalendarConnectionEntity existing) {
        String accountIdentifier = StringUtils.hasText(submitted)
                ? submitted.trim()
                : existing == null ? "" : existing.getAccountEmail();
        if (accountIdentifier.length() > 320) {
            throw new InvalidICloudCalendarException("Apple Account email is invalid");
        }
        if (EMAIL_PATTERN.matcher(accountIdentifier).matches()) {
            return accountIdentifier.toLowerCase(Locale.ROOT);
        }
        throw new InvalidICloudCalendarException("Apple Account email is invalid");
    }

    private String decrypt(ICloudCalendarConnectionEntity connection) {
        return secretCipher.decrypt(new SecretCipher.EncryptedSecret(
                connection.getPasswordCiphertext(), connection.getPasswordIv()
        ));
    }

    private ICloudCalendarConnectionEntity requiredConnection(UUID deviceId) {
        return connectionRepository.findById(deviceId)
                .orElseThrow(() -> new InvalidICloudCalendarException("iCloud calendar is not connected"));
    }

    private void validateDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidICloudCalendarException("iCloud calendar device is invalid");
        }
    }

    private ConnectionSnapshot snapshot(ICloudCalendarConnectionEntity connection, Instant now) {
        List<CalendarSnapshot> calendars = calendarRepository
                .findAllByDeviceIdOrderByDisplayNameAsc(connection.getDeviceId())
                .stream()
                .map(calendar -> new CalendarSnapshot(
                        calendar.getId(), calendar.getDisplayName(), calendar.isAllowed(), calendar.getUpdatedAt()
                ))
                .toList();
        long cachedEventCount = eventRepository.countByDeviceIdAndExpiresAtAfter(connection.getDeviceId(), now);
        Instant cacheExpiresAt = eventRepository
                .findFirstByDeviceIdAndExpiresAtAfterOrderByExpiresAtAsc(connection.getDeviceId(), now)
                .map(WorkdayCalendarEventEntity::getExpiresAt)
                .orElse(null);
        return new ConnectionSnapshot(
                connection.getDeviceId(), true, maskAccount(connection.getAccountEmail()), true,
                connection.getStatus(), connection.getLastFailureCode(), connection.getLastTestedAt(),
                connection.getLastSyncedAt(), cachedEventCount, cacheExpiresAt, calendars
        );
    }

    private String maskAccount(String accountIdentifier) {
        if (accountIdentifier.startsWith("+")) {
            int suffixLength = 4;
            int prefixLength = Math.min(4, accountIdentifier.length() - suffixLength - 1);
            int maskedLength = accountIdentifier.length() - prefixLength - suffixLength;
            return accountIdentifier.substring(0, prefixLength)
                    + "*".repeat(maskedLength)
                    + accountIdentifier.substring(accountIdentifier.length() - suffixLength);
        }
        int separator = accountIdentifier.indexOf('@');
        if (separator <= 0) {
            return "***";
        }
        String local = accountIdentifier.substring(0, separator);
        String masked = local.length() <= 2
                ? local.substring(0, 1) + "***"
                : local.substring(0, 2) + "***";
        return masked + accountIdentifier.substring(separator);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ConnectionCommand(String accountEmail, String appSpecificPassword) { }

    public record ConnectionTestSnapshot(
            boolean ok,
            String account,
            int discoveredCalendarCount,
            List<String> calendarNames
    ) { }

    public record CalendarSnapshot(UUID id, String displayName, boolean allowed, Instant updatedAt) { }

    public record ConnectionSnapshot(
            UUID deviceId,
            boolean configured,
            String account,
            boolean appSpecificPasswordConfigured,
            ICloudCalendarConnectionStatus status,
            ICloudCalendarFailureCode lastFailureCode,
            Instant lastTestedAt,
            Instant lastSyncedAt,
            long cachedEventCount,
            Instant cacheExpiresAt,
            List<CalendarSnapshot> calendars
    ) {
        static ConnectionSnapshot empty(UUID deviceId) {
            return new ConnectionSnapshot(
                    deviceId, false, null, false, null, null, null, null, 0, null, List.of()
            );
        }
    }

    public record CachedEventSnapshot(
            Instant startsAt,
            Instant endsAt,
            boolean allDay,
            boolean busy,
            boolean privateEvent,
            String title,
            String location
    ) { }

    private record ResolvedCredentials(String accountEmail, String appSpecificPassword, boolean newPassword) { }
    private record PendingEvent(UUID calendarId, ICloudCalDavClient.CalendarEvent event) { }
}
