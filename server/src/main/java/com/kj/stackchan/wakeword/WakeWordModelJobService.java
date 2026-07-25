package com.kj.stackchan.wakeword;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.device.DeviceWakeModelStatusService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WakeWordModelJobService implements DeviceWakeModelStatusService {

    private static final Duration STALE_INSTALL_AGE = Duration.ofMinutes(10);
    private static final EnumSet<WakeWordModelJobStatus> ACTIVE_STATUSES = EnumSet.of(
            WakeWordModelJobStatus.READY,
            WakeWordModelJobStatus.INSTALLING
    );

    private final WakeWordModelJobRepository jobRepository;
    private final DeviceRepository deviceRepository;
    private final EspSrWakeWordModelCatalog catalog;
    private final DeviceCommandGateway commandGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public WakeWordModelJobService(
            WakeWordModelJobRepository jobRepository,
            DeviceRepository deviceRepository,
            EspSrWakeWordModelCatalog catalog,
            DeviceCommandGateway commandGateway,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.deviceRepository = deviceRepository;
        this.catalog = catalog;
        this.commandGateway = commandGateway;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public WakeWordModelJobEntity create(UUID deviceId, String modelName) {
        WakeWordModelOption option = catalog.requireOption(modelName);
        validateCreate(deviceId);
        GeneratedWakeWordModel model = catalog.packageModel(option.modelName());
        try {
            return jobRepository.saveAndFlush(
                    new WakeWordModelJobEntity(deviceId, option.phrase(), model, clock.instant())
            );
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidWakeWordModelJobException();
        }
    }

    @Transactional(readOnly = true)
    public List<WakeWordModelJobEntity> list(UUID deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new WakeWordModelNotFoundException();
        }
        return jobRepository.findTop20ByDeviceIdOrderByCreatedAtDesc(deviceId);
    }

    public void dispatchReadyJobs() {
        List<WakeWordModelJobEntity> candidates = transactionTemplate.execute(status -> List.copyOf(
                jobRepository.findTop10ByStatusOrderByCreatedAtAsc(WakeWordModelJobStatus.READY)
        ));
        if (candidates == null) {
            return;
        }
        for (WakeWordModelJobEntity candidate : candidates) {
            if (!commandGateway.isConnected(candidate.getDeviceId())) {
                continue;
            }
            String commandId = UUID.randomUUID().toString();
            DispatchCommand command = transactionTemplate.execute(status -> jobRepository.findByIdForUpdate(candidate.getId())
                    .filter(job -> job.getStatus() == WakeWordModelJobStatus.READY)
                    .map(job -> {
                        job.markInstalling(commandId, clock.instant());
                        return new DispatchCommand(
                                job.getId(), job.getDeviceId(), job.getModelName(), job.getArtifactSha256(),
                                job.getArtifactSize(), commandId
                        );
                    })
                    .orElse(null));
            if (command == null) {
                continue;
            }
            boolean sent = commandGateway.installWakeModel(
                    command.deviceId(), command.jobId(), command.modelName(), command.sha256(),
                    command.artifactSize(), command.commandId()
            );
            if (!sent) {
                transactionTemplate.executeWithoutResult(status -> jobRepository.findById(command.jobId())
                        .filter(job -> job.getStatus() == WakeWordModelJobStatus.INSTALLING &&
                                command.commandId().equals(job.getCommandId()) && job.getCommandAccepted() == null)
                        .ifPresent(job -> job.returnToReady(clock.instant())));
            }
        }
    }

    @Transactional
    public boolean recordCommandAcknowledgement(UUID deviceId, String commandId, boolean accepted) {
        WakeWordModelJobEntity job = jobRepository.findByCommandId(commandId).orElse(null);
        if (job == null || !job.getDeviceId().equals(deviceId) ||
                job.getStatus() != WakeWordModelJobStatus.INSTALLING) {
            return false;
        }
        if (accepted) {
            job.markCommandAccepted(clock.instant());
        } else {
            job.markFailed("device_install_rejected", clock.instant());
        }
        return true;
    }

    @Override
    @Transactional
    public void record(UUID deviceId, UUID jobId, String status, String modelName, String sha256) {
        WakeWordModelJobEntity job = jobRepository.findByIdAndDeviceId(jobId, deviceId).orElse(null);
        if (job == null || job.getStatus() != WakeWordModelJobStatus.INSTALLING ||
                !job.getModelName().equals(modelName) || !job.getArtifactSha256().equals(sha256)) {
            return;
        }
        if ("INSTALLED".equals(status)) {
            job.markInstalled(clock.instant());
        } else if ("ROLLED_BACK".equals(status)) {
            job.markRolledBack(clock.instant());
        }
    }

    @Transactional(readOnly = true)
    public byte[] artifact(UUID jobId, UUID deviceId) {
        WakeWordModelJobEntity job = jobRepository.findByIdAndDeviceId(jobId, deviceId)
                .orElseThrow(WakeWordModelNotFoundException::new);
        if (job.getStatus() != WakeWordModelJobStatus.READY &&
                job.getStatus() != WakeWordModelJobStatus.INSTALLING) {
            throw new WakeWordModelNotFoundException();
        }
        byte[] artifact = job.getArtifact();
        if (artifact == null || artifact.length != job.getArtifactSize() ||
                !WakeWordModelPackageValidator.sha256(artifact).equals(job.getArtifactSha256())) {
            throw new WakeWordModelNotFoundException();
        }
        return artifact;
    }

    public void recoverStaleInstalls() {
        Instant staleBefore = clock.instant().minus(STALE_INSTALL_AGE);
        transactionTemplate.executeWithoutResult(status -> jobRepository
                .findAllByStatusAndUpdatedAtBefore(WakeWordModelJobStatus.INSTALLING, staleBefore)
                .forEach(job -> {
                    if (Boolean.TRUE.equals(job.getCommandAccepted())) {
                        job.markFailed("device_install_timeout", clock.instant());
                    } else {
                        job.returnToReady(clock.instant());
                    }
                }));
    }

    private void validateCreate(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId) ||
                jobRepository.existsByDeviceIdAndStatusIn(deviceId, ACTIVE_STATUSES)) {
            throw new InvalidWakeWordModelJobException();
        }
    }

    private record DispatchCommand(
            UUID jobId,
            UUID deviceId,
            String modelName,
            String sha256,
            int artifactSize,
            String commandId
    ) {
    }
}
