package com.kj.stackchan.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceCredentialService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String MISSING_REFRESH_TOKEN_HASH = "0".repeat(64);

    private final DeviceRepository deviceRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceCredentialService(DeviceRepository deviceRepository, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    public DeviceCredentialRotation rotateForPairing(DeviceEntity device) {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        device.rotateCredentials(hash(refreshToken), clock.instant());
        return new DeviceCredentialRotation(device, refreshToken);
    }

    public DeviceEntity authenticateRefresh(UUID deviceId, String refreshToken) {
        String candidateHash = hash(refreshToken == null ? "" : refreshToken);
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        String storedHash = device == null ? null : device.getRefreshTokenHash();
        String comparableStoredHash = storedHash == null ? MISSING_REFRESH_TOKEN_HASH : storedHash;
        boolean hashesMatch = MessageDigest.isEqual(
                comparableStoredHash.getBytes(StandardCharsets.US_ASCII),
                candidateHash.getBytes(StandardCharsets.US_ASCII)
        );

        if (device == null || storedHash == null || refreshToken == null || !hashesMatch) {
            throw new InvalidDeviceRefreshCredentialException();
        }
        return device;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record DeviceCredentialRotation(DeviceEntity device, String refreshToken) {
    }
}
