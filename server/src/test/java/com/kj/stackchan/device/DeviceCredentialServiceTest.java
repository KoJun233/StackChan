package com.kj.stackchan.device;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private DeviceRepository repository;

    private DeviceCredentialService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCredentialService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rotatesAHighEntropyRefreshTokenAndStoresOnlyItsHash() {
        DeviceEntity device = new DeviceEntity("stackchan-001", "1.0.0");
        DeviceCredentialService.DeviceCredentialRotation rotation = service.rotateForPairing(device);

        assertThat(rotation.refreshToken()).hasSize(43);
        assertThat(device.getRefreshTokenHash()).hasSize(64).doesNotContain(rotation.refreshToken());
        assertThat(device.getCredentialVersion()).isEqualTo(1);
    }

    @Test
    void authenticatesTheCurrentRefreshTokenAndRejectsReplacedOrUnknownCredentials() {
        DeviceEntity device = new DeviceEntity("stackchan-001", "1.0.0");
        String replaced = service.rotateForPairing(device).refreshToken();
        String current = service.rotateForPairing(device).refreshToken();
        UUID unknownDeviceId = UUID.randomUUID();
        when(repository.findById(device.getId())).thenReturn(Optional.of(device));
        when(repository.findById(unknownDeviceId)).thenReturn(Optional.empty());

        assertThat(service.authenticateRefresh(device.getId(), current)).isSameAs(device);
        assertThatThrownBy(() -> service.authenticateRefresh(device.getId(), replaced))
                .isInstanceOf(InvalidDeviceRefreshCredentialException.class);
        assertThatThrownBy(() -> service.authenticateRefresh(unknownDeviceId, current))
                .isInstanceOf(InvalidDeviceRefreshCredentialException.class);
    }

    @Test
    void usesTheSameGenericFailureAndDigestComparisonForEveryInvalidRefreshCredential() {
        DeviceEntity currentDevice = new DeviceEntity("stackchan-current-001", "1.0.0");
        String currentToken = service.rotateForPairing(currentDevice).refreshToken();
        DeviceEntity legacyDevice = new DeviceEntity("stackchan-legacy-001", "1.0.0");
        UUID unknownDeviceId = UUID.randomUUID();
        when(repository.findById(unknownDeviceId)).thenReturn(Optional.empty());
        when(repository.findById(legacyDevice.getId())).thenReturn(Optional.of(legacyDevice));
        when(repository.findById(currentDevice.getId())).thenReturn(Optional.of(currentDevice));

        assertGenericFailureUsesDigestComparison(unknownDeviceId, currentToken);
        assertGenericFailureUsesDigestComparison(legacyDevice.getId(), currentToken);
        assertGenericFailureUsesDigestComparison(currentDevice.getId(), null);
        assertGenericFailureUsesDigestComparison(currentDevice.getId(), "wrong-refresh-token");
    }

    private void assertGenericFailureUsesDigestComparison(UUID deviceId, String refreshToken) {
        try (MockedStatic<MessageDigest> messageDigest = mockStatic(MessageDigest.class, CALLS_REAL_METHODS)) {
            InvalidDeviceRefreshCredentialException failure = catchThrowableOfType(
                    () -> service.authenticateRefresh(deviceId, refreshToken),
                    InvalidDeviceRefreshCredentialException.class
            );

            assertThat(failure).hasMessage("Device refresh credential is invalid");
            messageDigest.verify(() -> MessageDigest.isEqual(
                    argThat(value -> value != null && value.length == 64),
                    argThat(value -> value != null && value.length == 64)
            ));
        }
    }
}
