package com.kj.stackchan.persona;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.kj.stackchan.role.CompanionRoleService;

@Service
public class PersonaService {

    private static final PersonaSnapshot DEFAULT_PERSONA = new PersonaSnapshot(
            "StackChan",
            PersonaTone.WARM,
            PersonaReplyLength.BALANCED,
            PersonaProactivity.BALANCED,
            "",
            "",
            null
    );

    private final PersonaSettingsRepository repository;
    private final Clock clock;
    private final CompanionRoleService roleService;

    public PersonaService(PersonaSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.roleService = null;
    }

    @Autowired
    public PersonaService(CompanionRoleService roleService, Clock clock) {
        this.repository = null;
        this.clock = clock;
        this.roleService = roleService;
    }

    @Transactional(readOnly = true)
    public PersonaSnapshot get() {
        if (roleService != null) return toSnapshot(roleService.getDefault());
        return repository.findById(PersonaSettingsEntity.CURRENT_SETTINGS_ID)
                .map(this::toSnapshot)
                .orElse(DEFAULT_PERSONA);
    }

    @Transactional
    public PersonaSnapshot save(PersonaCommand command) {
        ValidatedPersona validated = validate(command);
        if (roleService != null) {
            var current = roleService.getDefault();
            return toSnapshot(roleService.update(current.id(), new CompanionRoleService.RoleCommand(
                    validated.displayName(), validated.tone(), validated.replyLength(), validated.proactivity(),
                    current.backgroundInstructions(), validated.topicBoundaries(), validated.taboos(),
                    current.ttsVoiceOverride())));
        }
        Instant now = clock.instant();
        PersonaSettingsEntity entity = repository.findById(PersonaSettingsEntity.CURRENT_SETTINGS_ID)
                .orElseGet(() -> new PersonaSettingsEntity(
                        validated.displayName(),
                        validated.tone(),
                        validated.replyLength(),
                        validated.proactivity(),
                        validated.topicBoundaries(),
                        validated.taboos(),
                        now
                ));
        entity.update(
                validated.displayName(),
                validated.tone(),
                validated.replyLength(),
                validated.proactivity(),
                validated.topicBoundaries(),
                validated.taboos(),
                now
        );
        return toSnapshot(repository.save(entity));
    }

    private ValidatedPersona validate(PersonaCommand command) {
        String displayName = normalize(command.displayName(), 80, "Persona display name is invalid", false);
        String topicBoundaries = normalize(command.topicBoundaries(), 2000, "Persona topic boundaries are invalid", true);
        String taboos = normalize(command.taboos(), 2000, "Persona taboos are invalid", true);
        if (command.tone() == null || command.replyLength() == null || command.proactivity() == null) {
            throw new InvalidPersonaException("Persona options are invalid");
        }
        return new ValidatedPersona(
                displayName,
                command.tone(),
                command.replyLength(),
                command.proactivity(),
                topicBoundaries,
                taboos
        );
    }

    private String normalize(String value, int maxLength, String error, boolean allowBlank) {
        String normalized = value == null ? "" : value.trim();
        if ((!allowBlank && normalized.isBlank()) || normalized.length() > maxLength) {
            throw new InvalidPersonaException(error);
        }
        return normalized;
    }

    private PersonaSnapshot toSnapshot(PersonaSettingsEntity entity) {
        return new PersonaSnapshot(
                entity.getDisplayName(),
                entity.getTone(),
                entity.getReplyLength(),
                entity.getProactivity(),
                entity.getTopicBoundaries(),
                entity.getTaboos(),
                entity.getUpdatedAt()
        );
    }

    private PersonaSnapshot toSnapshot(CompanionRoleService.RoleSnapshot role) {
        return new PersonaSnapshot(role.name(), role.tone(), role.replyLength(), role.proactivity(),
                role.topicBoundaries(), role.taboos(), role.updatedAt());
    }

    public record PersonaCommand(
            String displayName,
            PersonaTone tone,
            PersonaReplyLength replyLength,
            PersonaProactivity proactivity,
            String topicBoundaries,
            String taboos
    ) {
    }

    public record PersonaSnapshot(
            String displayName,
            PersonaTone tone,
            PersonaReplyLength replyLength,
            PersonaProactivity proactivity,
            String topicBoundaries,
            String taboos,
            Instant updatedAt
    ) {
    }

    private record ValidatedPersona(
            String displayName,
            PersonaTone tone,
            PersonaReplyLength replyLength,
            PersonaProactivity proactivity,
            String topicBoundaries,
            String taboos
    ) {
    }
}
