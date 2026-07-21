package com.kj.stackchan.device;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class PairingService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int PAIRING_CODE_BYTES = 9;

    private final PairingCodeRepository pairingCodeRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCredentialService deviceCredentialService;
    private final DeviceConnectionRegistry connectionRegistry;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PairingService(
            PairingCodeRepository pairingCodeRepository,
            DeviceRepository deviceRepository,
            DeviceCredentialService deviceCredentialService,
            DeviceConnectionRegistry connectionRegistry,
            Clock clock
    ) {
        this.pairingCodeRepository = pairingCodeRepository;
        this.deviceRepository = deviceRepository;
        this.deviceCredentialService = deviceCredentialService;
        this.connectionRegistry = connectionRegistry;
        this.clock = clock;
    }

    @Transactional
    public PairingCodeEntity createCode(String createdBy) {
        Instant createdAt = clock.instant();
        PairingCodeEntity pairingCode = new PairingCodeEntity(
                nextToken(),
                createdBy,
                createdAt.plus(CODE_TTL),
                createdAt
        );
        return pairingCodeRepository.save(pairingCode);
    }

    @Transactional
    public Optional<PairingClaim> claim(String codeValue, String hardwareId, String firmwareVersion) {
        Instant now = clock.instant();
        Optional<PairingCodeEntity> pairingCode = pairingCodeRepository.findByValueForUpdate(codeValue);

        if (pairingCode.isEmpty() || pairingCode.get().isConsumed() || !pairingCode.get().getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }

        DeviceEntity device = deviceRepository.findByHardwareIdForUpdate(hardwareId)
                .orElseGet(() -> new DeviceEntity(hardwareId, firmwareVersion));
        long previousVersion = device.getCredentialVersion();
        device.prepareForRepairing(firmwareVersion);
        DeviceCredentialService.DeviceCredentialRotation rotation = deviceCredentialService.rotateForPairing(device);
        deviceRepository.save(device);
        pairingCode.get().markConsumed(device.getId());
        revokePreviousSessionAfterCommit(device.getId(), device.getCredentialVersion());
        return Optional.of(new PairingClaim(device, rotation.refreshToken(), previousVersion));
    }

    private void revokePreviousSessionAfterCommit(UUID deviceId, long currentCredentialVersion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            connectionRegistry.revokeCredentials(deviceId, currentCredentialVersion);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                connectionRegistry.revokeCredentials(deviceId, currentCredentialVersion);
            }
        });
    }

    private String nextToken() {
        byte[] tokenBytes = new byte[PAIRING_CODE_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public record PairingClaim(
            DeviceEntity device,
            String refreshToken,
            long credentialVersionBeforeRotation
    ) {
    }
}
