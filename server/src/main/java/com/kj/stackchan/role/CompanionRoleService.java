package com.kj.stackchan.role;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.notification.NotificationIntegrationRepository;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanionRoleService {
    private static final List<VoiceTurnStatus> ACTIVE_TURNS = List.of(
            VoiceTurnStatus.IN_PROGRESS, VoiceTurnStatus.RESPONSE_READY
    );

    private final CompanionRoleRepository roleRepository;
    private final DeviceActiveRoleRepository activeRoleRepository;
    private final DeviceRepository deviceRepository;
    private final VoiceTurnRepository voiceTurnRepository;
    private final ReminderRepository reminderRepository;
    private final Clock clock;
    private final NotificationIntegrationRepository notificationIntegrationRepository;

    public CompanionRoleService(CompanionRoleRepository roleRepository,
                                DeviceActiveRoleRepository activeRoleRepository,
                                DeviceRepository deviceRepository,
                                VoiceTurnRepository voiceTurnRepository,
                                ReminderRepository reminderRepository,
                                Clock clock,
                                NotificationIntegrationRepository notificationIntegrationRepository) {
        this.roleRepository = roleRepository;
        this.activeRoleRepository = activeRoleRepository;
        this.deviceRepository = deviceRepository;
        this.voiceTurnRepository = voiceTurnRepository;
        this.reminderRepository = reminderRepository;
        this.clock = clock;
        this.notificationIntegrationRepository = notificationIntegrationRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleSnapshot> list() {
        return roleRepository.findAllByOrderByDefaultRoleDescArchivedAtAscUpdatedAtDescIdAsc()
                .stream().map(this::toSnapshot).toList();
    }

    @Transactional(readOnly = true) public RoleSnapshot get(UUID id) { return toSnapshot(find(id)); }
    @Transactional(readOnly = true) public RoleSnapshot getDefault() {
        return toSnapshot(roleRepository.findByDefaultRoleTrue().orElseThrow(RoleNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public RoleSnapshot getActive(UUID deviceId) {
        requireDevice(deviceId);
        UUID roleId = activeRoleRepository.findByDeviceId(deviceId)
                .map(DeviceActiveRoleEntity::getRoleId).orElse(CompanionRoleEntity.DEFAULT_ROLE_ID);
        return get(roleId);
    }

    @Transactional
    public RoleSnapshot create(RoleCommand command) {
        ValidatedRole role = validate(command);
        Instant now = clock.instant();
        return toSnapshot(roleRepository.save(new CompanionRoleEntity(
                role.name(), role.tone(), role.replyLength(), role.proactivity(), role.backgroundInstructions(),
                role.topicBoundaries(), role.taboos(), role.ttsVoiceOverride(), role.expressionThemeColor(), now
        )));
    }

    @Transactional
    public RoleSnapshot update(UUID id, RoleCommand command) {
        ValidatedRole role = validate(command);
        CompanionRoleEntity entity = roleRepository.findByIdForUpdate(id).orElseThrow(RoleNotFoundException::new);
        entity.update(role.name(), role.tone(), role.replyLength(), role.proactivity(), role.backgroundInstructions(),
                role.topicBoundaries(), role.taboos(), role.ttsVoiceOverride(), role.expressionThemeColor(),
                clock.instant());
        return toSnapshot(entity);
    }

    @Transactional
    public RoleSnapshot switchActive(UUID deviceId, UUID roleId) {
        requireDevice(deviceId);
        CompanionRoleEntity role = find(roleId);
        if (role.getArchivedAt() != null) throw new RoleConflictException("Archived role cannot become active");
        if (voiceTurnRepository.existsByDeviceIdAndStatusIn(deviceId, ACTIVE_TURNS)) {
            throw new RoleConflictException("Device has an active voice turn");
        }
        if (reminderRepository.existsByDeviceIdAndStatus(deviceId, ReminderStatus.DISPATCHED)) {
            throw new RoleConflictException("Device has a dispatched reminder");
        }
        Instant now = clock.instant();
        DeviceActiveRoleEntity mapping = activeRoleRepository.findByDeviceId(deviceId)
                .orElseGet(() -> new DeviceActiveRoleEntity(deviceId, roleId, now));
        mapping.switchTo(roleId, now);
        activeRoleRepository.save(mapping);
        return toSnapshot(role);
    }

    @Transactional
    public RoleSnapshot switchActiveFromVoice(UUID deviceId, String roleName) {
        String normalized = normalize(roleName, 80, false);
        CompanionRoleEntity role = roleRepository.findFirstByNameIgnoreCaseAndArchivedAtIsNull(normalized)
                .orElseThrow(RoleNotFoundException::new);
        return switchActive(deviceId, role.getId());
    }

    @Transactional
    public RoleSnapshot archive(UUID id) {
        CompanionRoleEntity role = roleRepository.findByIdForUpdate(id).orElseThrow(RoleNotFoundException::new);
        if (role.isDefaultRole()) throw new RoleConflictException("Default role cannot be archived");
        if (role.getArchivedAt() != null) return toSnapshot(role);
        Instant now = clock.instant();
        role.archive(now);
        UUID defaultId = getDefault().id();
        activeRoleRepository.findAllByRoleId(id).forEach(mapping -> mapping.switchTo(defaultId, now));
        reminderRepository.cancelFutureByRoleId(id, now);
        notificationIntegrationRepository.disableAllByRoleId(id, now);
        return toSnapshot(role);
    }

    @Transactional
    public RoleSnapshot restore(UUID id) {
        CompanionRoleEntity role = roleRepository.findByIdForUpdate(id).orElseThrow(RoleNotFoundException::new);
        role.restore(clock.instant());
        return toSnapshot(role);
    }

    private CompanionRoleEntity find(UUID id) {
        return roleRepository.findById(id).orElseThrow(RoleNotFoundException::new);
    }
    private void requireDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) throw new InvalidRoleException("Role device is invalid");
    }
    private ValidatedRole validate(RoleCommand command) {
        if (command == null || command.tone() == null || command.replyLength() == null || command.proactivity() == null) {
            throw new InvalidRoleException("Role options are invalid");
        }
        return new ValidatedRole(normalize(command.name(), 80, false), command.tone(), command.replyLength(),
                command.proactivity(), normalize(command.backgroundInstructions(), 4000, true),
                normalize(command.topicBoundaries(), 2000, true), normalize(command.taboos(), 2000, true),
                optional(command.ttsVoiceOverride(), 160), themeColor(command.expressionThemeColor()));
    }
    private String normalize(String value, int maxLength, boolean allowBlank) {
        String normalized = value == null ? "" : value.trim();
        if ((!allowBlank && normalized.isBlank()) || normalized.length() > maxLength) {
            throw new InvalidRoleException("Role text is invalid");
        }
        return normalized;
    }
    private String optional(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) throw new InvalidRoleException("Role voice is invalid");
        return normalized.isBlank() ? null : normalized;
    }
    private String themeColor(String value) {
        String normalized = value == null || value.isBlank() ? "#FF4FA3" : value.trim().toUpperCase();
        if (!normalized.matches("^#[0-9A-F]{6}$")) throw new InvalidRoleException("Role color is invalid");
        return normalized;
    }
    private RoleSnapshot toSnapshot(CompanionRoleEntity role) {
        return new RoleSnapshot(role.getId(), role.getName(), role.getTone(), role.getReplyLength(), role.getProactivity(),
                role.getBackgroundInstructions(), role.getTopicBoundaries(), role.getTaboos(), role.isDefaultRole(),
                role.getTtsVoiceOverride(), role.getExpressionThemeColor(), role.getArchivedAt(),
                role.getCreatedAt(), role.getUpdatedAt());
    }

    public record RoleCommand(String name, PersonaTone tone, PersonaReplyLength replyLength,
                              PersonaProactivity proactivity, String backgroundInstructions,
                              String topicBoundaries, String taboos, String ttsVoiceOverride,
                              String expressionThemeColor) {
        public RoleCommand(String name, PersonaTone tone, PersonaReplyLength replyLength,
                           PersonaProactivity proactivity, String backgroundInstructions,
                           String topicBoundaries, String taboos) {
            this(name, tone, replyLength, proactivity, backgroundInstructions, topicBoundaries, taboos,
                    null, "#FF4FA3");
        }
        public RoleCommand(String name, PersonaTone tone, PersonaReplyLength replyLength,
                           PersonaProactivity proactivity, String backgroundInstructions,
                           String topicBoundaries, String taboos, String ttsVoiceOverride) {
            this(name, tone, replyLength, proactivity, backgroundInstructions, topicBoundaries, taboos,
                    ttsVoiceOverride, "#FF4FA3");
        }
    }
    public record RoleSnapshot(UUID id, String name, PersonaTone tone, PersonaReplyLength replyLength,
                               PersonaProactivity proactivity, String backgroundInstructions,
                               String topicBoundaries, String taboos, boolean defaultRole, String ttsVoiceOverride,
                               String expressionThemeColor,
                               Instant archivedAt, Instant createdAt, Instant updatedAt) {
        public RoleSnapshot(UUID id, String name, PersonaTone tone, PersonaReplyLength replyLength,
                            PersonaProactivity proactivity, String backgroundInstructions,
                            String topicBoundaries, String taboos, boolean defaultRole,
                            Instant archivedAt, Instant createdAt, Instant updatedAt) {
            this(id, name, tone, replyLength, proactivity, backgroundInstructions, topicBoundaries, taboos,
                    defaultRole, null, "#FF4FA3", archivedAt, createdAt, updatedAt);
        }
        public RoleSnapshot(UUID id, String name, PersonaTone tone, PersonaReplyLength replyLength,
                            PersonaProactivity proactivity, String backgroundInstructions,
                            String topicBoundaries, String taboos, boolean defaultRole, String ttsVoiceOverride,
                            Instant archivedAt, Instant createdAt, Instant updatedAt) {
            this(id, name, tone, replyLength, proactivity, backgroundInstructions, topicBoundaries, taboos,
                    defaultRole, ttsVoiceOverride, "#FF4FA3", archivedAt, createdAt, updatedAt);
        }
    }
    private record ValidatedRole(String name, PersonaTone tone, PersonaReplyLength replyLength,
                                 PersonaProactivity proactivity, String backgroundInstructions,
                                 String topicBoundaries, String taboos, String ttsVoiceOverride,
                                 String expressionThemeColor) {}
}
