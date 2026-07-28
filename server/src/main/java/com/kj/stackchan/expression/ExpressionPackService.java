package com.kj.stackchan.expression;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExpressionPackService {

    private static final Duration STALE_INSTALL_AGE = Duration.ofMinutes(10);

    private final ExpressionPackRepository packRepository;
    private final ExpressionPackStateRepository stateRepository;
    private final DeviceExpressionPackRepository mappingRepository;
    private final DeviceRepository deviceRepository;
    private final ExpressionPackCompiler compiler;
    private final DeviceCommandGateway commandGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ExpressionPackService(
            ExpressionPackRepository packRepository,
            ExpressionPackStateRepository stateRepository,
            DeviceExpressionPackRepository mappingRepository,
            DeviceRepository deviceRepository,
            ExpressionPackCompiler compiler,
            DeviceCommandGateway commandGateway,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.packRepository = packRepository;
        this.stateRepository = stateRepository;
        this.mappingRepository = mappingRepository;
        this.deviceRepository = deviceRepository;
        this.compiler = compiler;
        this.commandGateway = commandGateway;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public ExpressionPackEntity create(String rawName, String rawDescription, Map<ExpressionState, byte[]> images) {
        String name = normalizeRequired(rawName, 80);
        String description = normalizeOptional(rawDescription, 240);
        GeneratedExpressionPack generated = compiler.compile(images);
        ExpressionPackEntity pack = packRepository.save(new ExpressionPackEntity(
                name, description, generated, clock.instant()
        ));
        for (ExpressionState state : ExpressionState.values()) {
            stateRepository.save(new ExpressionPackStateEntity(
                    pack.getId(), state, generated.images().get(state), generated.imageSha256().get(state)
            ));
        }
        return pack;
    }

    @Transactional(readOnly = true)
    public List<ExpressionPackEntity> list() {
        return packRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public ExpressionPackStateEntity state(UUID packId, String stateName) {
        ExpressionState state = ExpressionState.fromWireName(stateName);
        return stateRepository.findByPackIdAndStateName(packId, state.wireName())
                .orElseThrow(ExpressionPackNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public DeviceExpressionPackEntity deviceSelection(UUID deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new ExpressionPackNotFoundException();
        }
        return mappingRepository.findById(deviceId)
                .orElseGet(() -> new DeviceExpressionPackEntity(deviceId, clock.instant()));
    }

    @Transactional
    public DeviceExpressionPackEntity activate(UUID deviceId, UUID packId) {
        if (!deviceRepository.existsById(deviceId) || !packRepository.existsById(packId)) {
            throw new InvalidExpressionPackException();
        }
        DeviceExpressionPackEntity mapping = mappingRepository.findByDeviceIdForUpdate(deviceId)
                .orElseGet(() -> new DeviceExpressionPackEntity(deviceId, clock.instant()));
        mapping.enable(packId, clock.instant());
        return mappingRepository.save(mapping);
    }

    @Transactional
    public DeviceExpressionPackEntity deactivate(UUID deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new InvalidExpressionPackException();
        }
        DeviceExpressionPackEntity mapping = mappingRepository.findByDeviceIdForUpdate(deviceId)
                .orElseGet(() -> new DeviceExpressionPackEntity(deviceId, clock.instant()));
        mapping.disable(clock.instant());
        DeviceExpressionPackEntity saved = mappingRepository.save(mapping);
        if (commandGateway.isConnected(deviceId)) {
            commandGateway.clearExpressionPack(deviceId, UUID.randomUUID().toString());
        }
        return saved;
    }

    @Transactional
    public void delete(UUID packId) {
        if (!packRepository.existsById(packId)) {
            throw new ExpressionPackNotFoundException();
        }
        if (mappingRepository.existsByPackIdAndEnabledTrue(packId)) {
            throw new InvalidExpressionPackException();
        }
        packRepository.deleteById(packId);
    }

    public void dispatchReady() {
        List<DeviceExpressionPackEntity> candidates = transactionTemplate.execute(status -> List.copyOf(
                mappingRepository.findTop10ByStatusOrderByUpdatedAtAsc(DeviceExpressionPackStatus.READY)
        ));
        if (candidates == null) {
            return;
        }
        for (DeviceExpressionPackEntity candidate : candidates) {
            if (!commandGateway.isConnected(candidate.getDeviceId())) {
                continue;
            }
            InstallCommand command = transactionTemplate.execute(status ->
                    mappingRepository.findByDeviceIdForUpdate(candidate.getDeviceId())
                            .filter(mapping -> mapping.isEnabled() &&
                                    mapping.getStatus() == DeviceExpressionPackStatus.READY)
                            .flatMap(mapping -> packRepository.findById(mapping.getPackId()).map(pack -> {
                                String commandId = UUID.randomUUID().toString();
                                mapping.markInstalling(commandId, clock.instant());
                                return new InstallCommand(
                                        mapping.getDeviceId(),
                                        pack.getId(),
                                        pack.getArtifactSha256(),
                                        pack.getArtifactSize(),
                                        commandId
                                );
                            }))
                            .orElse(null));
            if (command == null) {
                continue;
            }
            if (!commandGateway.installExpressionPack(
                    command.deviceId(), command.packId(), command.sha256(),
                    command.artifactSize(), command.commandId()
            )) {
                transactionTemplate.executeWithoutResult(status ->
                        mappingRepository.findByDeviceIdForUpdate(command.deviceId())
                                .filter(mapping -> command.commandId().equals(mapping.getCommandId()) &&
                                        mapping.getStatus() == DeviceExpressionPackStatus.INSTALLING)
                                .ifPresent(mapping -> mapping.returnToReady(clock.instant())));
            }
        }
    }

    @Transactional
    public boolean recordCommandAcknowledgement(UUID deviceId, String commandId, boolean accepted) {
        DeviceExpressionPackEntity mapping = mappingRepository.findByCommandId(commandId).orElse(null);
        if (mapping == null || !mapping.getDeviceId().equals(deviceId) ||
                mapping.getStatus() != DeviceExpressionPackStatus.INSTALLING) {
            return false;
        }
        if (accepted) {
            mapping.markActive(clock.instant());
        } else {
            mapping.markFailed("device_install_rejected", clock.instant());
        }
        return true;
    }

    @Transactional(readOnly = true)
    public byte[] artifact(UUID packId, UUID deviceId) {
        DeviceExpressionPackEntity mapping = mappingRepository.findById(deviceId)
                .filter(value -> value.isEnabled() && packId.equals(value.getPackId()))
                .orElseThrow(ExpressionPackNotFoundException::new);
        ExpressionPackEntity pack = packRepository.findById(mapping.getPackId())
                .orElseThrow(ExpressionPackNotFoundException::new);
        byte[] artifact = pack.getArtifact();
        if (artifact.length != pack.getArtifactSize() ||
                !ExpressionPackCompiler.sha256(artifact).equals(pack.getArtifactSha256())) {
            throw new ExpressionPackNotFoundException();
        }
        return artifact;
    }

    public void recoverStaleInstalls() {
        Instant staleBefore = clock.instant().minus(STALE_INSTALL_AGE);
        transactionTemplate.executeWithoutResult(status -> mappingRepository
                .findAllByStatusAndUpdatedAtBefore(DeviceExpressionPackStatus.INSTALLING, staleBefore)
                .forEach(mapping -> mapping.returnToReady(clock.instant())));
    }

    public void syncConnectedDevice(UUID deviceId) {
        DeviceExpressionPackEntity mapping = transactionTemplate.execute(status ->
                mappingRepository.findById(deviceId).orElse(null));
        if (mapping == null || !mapping.isEnabled() || mapping.getPackId() == null) {
            commandGateway.clearExpressionPack(deviceId, UUID.randomUUID().toString());
            return;
        }
        ExpressionPackEntity pack = transactionTemplate.execute(status ->
                packRepository.findById(mapping.getPackId()).orElse(null));
        if (pack != null) {
            commandGateway.installExpressionPack(
                    deviceId,
                    pack.getId(),
                    pack.getArtifactSha256(),
                    pack.getArtifactSize(),
                    UUID.randomUUID().toString()
            );
        }
    }

    private static String normalizeRequired(String value, int maximum) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new InvalidExpressionPackException();
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maximum) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > maximum) {
            throw new InvalidExpressionPackException();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private record InstallCommand(
            UUID deviceId,
            UUID packId,
            String sha256,
            int artifactSize,
            String commandId
    ) {
    }
}
