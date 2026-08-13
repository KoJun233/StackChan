package com.kj.stackchan.role;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.notification.NotificationIntegrationRepository;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.speech.VoiceTurnRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CompanionRoleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");

    @Test
    void refusesDefaultArchiveAndBlocksSwitchDuringVoiceTurn() {
        var roles = mock(CompanionRoleRepository.class);
        var active = mock(DeviceActiveRoleRepository.class);
        var devices = mock(DeviceRepository.class);
        var turns = mock(VoiceTurnRepository.class);
        var reminders = mock(ReminderRepository.class);
        var integrations = mock(NotificationIntegrationRepository.class);
        CompanionRoleEntity role = role("助理");
        UUID deviceId = UUID.randomUUID();
        when(devices.existsById(deviceId)).thenReturn(true);
        when(roles.findById(role.getId())).thenReturn(Optional.of(role));
        when(turns.existsByDeviceIdAndStatusIn(eq(deviceId), any())).thenReturn(true);
        CompanionRoleService service = new CompanionRoleService(roles, active, devices, turns, reminders,
                Clock.fixed(NOW, ZoneOffset.UTC), integrations);

        assertThatThrownBy(() -> service.switchActive(deviceId, role.getId()))
                .isInstanceOf(RoleConflictException.class);
    }

    @Test
    void archiveSwitchesDevicesCancelsRemindersAndDisablesIntegrations() {
        var roles = mock(CompanionRoleRepository.class);
        var active = mock(DeviceActiveRoleRepository.class);
        var devices = mock(DeviceRepository.class);
        var turns = mock(VoiceTurnRepository.class);
        var reminders = mock(ReminderRepository.class);
        var integrations = mock(NotificationIntegrationRepository.class);
        CompanionRoleEntity role = role("温柔陪伴");
        CompanionRoleEntity defaultRole = mock(CompanionRoleEntity.class);
        when(defaultRole.getId()).thenReturn(CompanionRoleEntity.DEFAULT_ROLE_ID);
        when(defaultRole.isDefaultRole()).thenReturn(true);
        when(roles.findByIdForUpdate(role.getId())).thenReturn(Optional.of(role));
        when(roles.findByDefaultRoleTrue()).thenReturn(Optional.of(defaultRole));
        DeviceActiveRoleEntity mapping = new DeviceActiveRoleEntity(UUID.randomUUID(), role.getId(), NOW.minusSeconds(1));
        when(active.findAllByRoleId(role.getId())).thenReturn(List.of(mapping));
        CompanionRoleService service = new CompanionRoleService(roles, active, devices, turns, reminders,
                Clock.fixed(NOW, ZoneOffset.UTC), integrations);

        var archived = service.archive(role.getId());

        assertThat(archived.archivedAt()).isEqualTo(NOW);
        assertThat(mapping.getRoleId()).isEqualTo(CompanionRoleEntity.DEFAULT_ROLE_ID);
        verify(reminders).cancelFutureByRoleId(role.getId(), NOW);
        verify(integrations).disableAllByRoleId(role.getId(), NOW);
    }

    private CompanionRoleEntity role(String name) {
        return new CompanionRoleEntity(name, PersonaTone.WARM, PersonaReplyLength.BALANCED,
                PersonaProactivity.BALANCED, "", "", "", NOW.minusSeconds(60));
    }
}
