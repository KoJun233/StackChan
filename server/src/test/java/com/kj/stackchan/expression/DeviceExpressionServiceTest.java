package com.kj.stackchan.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.role.CompanionRoleService;
import org.junit.jupiter.api.Test;

class DeviceExpressionServiceTest {
    @Test
    void sendsOnlyValidatedSemanticsToCapableDevice() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        CompanionRoleService roles = mock(CompanionRoleService.class);
        DeviceEntity device = mock(DeviceEntity.class);
        when(device.isDynamicExpressionSupported()).thenReturn(true);
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        when(roles.get(roleId)).thenReturn(new CompanionRoleService.RoleSnapshot(
                roleId, "助理", PersonaTone.WARM, PersonaReplyLength.BALANCED,
                PersonaProactivity.BALANCED, "", "", "", false, null, "#3A7BFF",
                null, Instant.EPOCH, Instant.EPOCH));
        when(gateway.configureExpression(deviceId, "#3A7BFF", "HAPPY", "STRONG", 12))
                .thenReturn(true);
        DeviceExpressionService service = new DeviceExpressionService(devices, gateway, roles);

        boolean sent = service.apply(deviceId, roleId, new ExpressionSuggestionParser.Suggestion(
                "正文", CompanionEmotion.HAPPY, EmotionIntensity.STRONG, 12));

        assertThat(sent).isTrue();
        verify(gateway).configureExpression(deviceId, "#3A7BFF", "HAPPY", "STRONG", 12);
    }

    @Test
    void doesNotSendToLegacyDevice() {
        UUID deviceId = UUID.randomUUID();
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        CompanionRoleService roles = mock(CompanionRoleService.class);
        DeviceEntity device = mock(DeviceEntity.class);
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        DeviceExpressionService service = new DeviceExpressionService(devices, gateway, roles);

        assertThat(service.apply(deviceId, UUID.randomUUID(),
                ExpressionSuggestionParser.parse("普通回答"))).isFalse();
        verifyNoInteractions(gateway, roles);
    }

    @Test
    void synchronizesTheActiveRoleThemeAfterReconnect() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        CompanionRoleService roles = mock(CompanionRoleService.class);
        DeviceEntity device = mock(DeviceEntity.class);
        when(device.isDynamicExpressionSupported()).thenReturn(true);
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        CompanionRoleService.RoleSnapshot role = new CompanionRoleService.RoleSnapshot(
                roleId, "助理", PersonaTone.WARM, PersonaReplyLength.BALANCED,
                PersonaProactivity.BALANCED, "", "", "", false, null, "#3A7BFF",
                null, Instant.EPOCH, Instant.EPOCH);
        when(roles.getActive(deviceId)).thenReturn(role);
        when(roles.get(roleId)).thenReturn(role);
        when(gateway.configureExpression(deviceId, "#3A7BFF", "NEUTRAL", "MEDIUM", 5))
                .thenReturn(true);

        assertThat(new DeviceExpressionService(devices, gateway, roles)
                .synchronizeActiveRoleTheme(deviceId)).isTrue();
        verify(gateway).configureExpression(deviceId, "#3A7BFF", "NEUTRAL", "MEDIUM", 5);
    }

    @Test
    void persistsAndAppliesBoundedAdaptiveFrameRate() {
        UUID deviceId = UUID.randomUUID();
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        DeviceEntity device = mock(DeviceEntity.class);
        when(device.isDynamicExpressionSupported()).thenReturn(true);
        when(device.getExpressionFpsMode()).thenReturn("ADAPTIVE");
        when(device.getExpressionMinFps()).thenReturn(24);
        when(device.getExpressionMaxFps()).thenReturn(57);
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        when(gateway.configureExpressionFrameRate(deviceId, "ADAPTIVE", 24, 57)).thenReturn(true);
        DeviceExpressionService service = new DeviceExpressionService(
                devices, gateway, mock(CompanionRoleService.class));

        DeviceExpressionService.FrameRateSettings result =
                service.configureFrameRate(deviceId, "ADAPTIVE", 24, 57);

        assertThat(result).isEqualTo(new DeviceExpressionService.FrameRateSettings(
                "ADAPTIVE", 24, 57, true));
        verify(device).configureExpressionFrameRate("ADAPTIVE", 24, 57);
        verify(devices).save(device);
    }

    @Test
    void rejectsInvalidFixedFrameRateBeforePersistence() {
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceExpressionService service = new DeviceExpressionService(
                devices, mock(DeviceCommandGateway.class), mock(CompanionRoleService.class));

        assertThatThrownBy(() -> service.configureFrameRate(
                UUID.randomUUID(), "FIXED", 30, 60)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(devices);
    }

    @Test
    void rejectsFrameRatesOutsideOneThroughSixty() {
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceExpressionService service = new DeviceExpressionService(
                devices, mock(DeviceCommandGateway.class), mock(CompanionRoleService.class));

        assertThatThrownBy(() -> service.configureFrameRate(
                UUID.randomUUID(), "FIXED", 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configureFrameRate(
                UUID.randomUUID(), "ADAPTIVE", 1, 61)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(devices);
    }

    @Test
    void previewsOnlyKnownSemanticsOnCapableDevice() {
        UUID deviceId = UUID.randomUUID();
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        DeviceEntity device = mock(DeviceEntity.class);
        when(device.isDynamicExpressionSupported()).thenReturn(true);
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        when(gateway.previewExpression(deviceId, "SYSTEM", "NO_SPEECH", 5)).thenReturn(true);
        DeviceExpressionService service = new DeviceExpressionService(
                devices, gateway, mock(CompanionRoleService.class));

        assertThat(service.preview(deviceId, "SYSTEM", "NO_SPEECH", 5)).isTrue();
        assertThatThrownBy(() -> service.preview(deviceId, "SYSTEM", "ROOT_SHELL", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
