package com.kj.stackchan.firmwareupdate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceFirmwareUpdateStatusService;
import com.kj.stackchan.device.DeviceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FirmwareUpdateService implements DeviceFirmwareUpdateStatusService {

    private static final Duration STALE_INSTALL_AGE = Duration.ofMinutes(15);
    private static final EnumSet<FirmwareUpdateStatus> ACTIVE_STATUSES = EnumSet.of(
            FirmwareUpdateStatus.READY, FirmwareUpdateStatus.INSTALLING
    );

    private final FirmwareReleaseRepository releaseRepository;
    private final FirmwareUpdateJobRepository jobRepository;
    private final FirmwareArtifactValidator artifactValidator;
    private final DeviceRepository deviceRepository;
    private final DeviceCommandGateway commandGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public FirmwareUpdateService(
            FirmwareReleaseRepository releaseRepository,
            FirmwareUpdateJobRepository jobRepository,
            FirmwareArtifactValidator artifactValidator,
            DeviceRepository deviceRepository,
            DeviceCommandGateway commandGateway,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.releaseRepository = releaseRepository;
        this.jobRepository = jobRepository;
        this.artifactValidator = artifactValidator;
        this.deviceRepository = deviceRepository;
        this.commandGateway = commandGateway;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public FirmwareReleaseEntity importRelease(byte[] bytes, String version) {
        ValidatedFirmwareArtifact validated = artifactValidator.validate(bytes, version);
        FirmwareReleaseEntity existing = releaseRepository.findByArtifactSha256(validated.sha256()).orElse(null);
        if (existing != null) {
            if (!existing.getVersion().equals(validated.version())) {
                throw new InvalidFirmwareUpdateException();
            }
            return existing;
        }
        try {
            return releaseRepository.saveAndFlush(new FirmwareReleaseEntity(validated, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidFirmwareUpdateException();
        }
    }

    @Transactional(readOnly = true)
    public List<FirmwareReleaseEntity> listReleases() {
        return releaseRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public FirmwareUpdateJobEntity createJob(
            UUID deviceId,
            UUID releaseId,
            String confirmedCurrentVersion
    ) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(InvalidFirmwareUpdateException::new);
        FirmwareReleaseEntity release = releaseRepository.findById(releaseId)
                .orElseThrow(InvalidFirmwareUpdateException::new);
        String confirmation = confirmedCurrentVersion == null ? "" : confirmedCurrentVersion.strip();
        if (!device.isApplicationOtaSupported() || !commandGateway.isConnected(deviceId) ||
                !device.getFirmwareVersion().equals(confirmation) ||
                device.getFirmwareVersion().equals(release.getVersion()) ||
                jobRepository.existsByDeviceIdAndStatusIn(deviceId, ACTIVE_STATUSES)) {
            throw new InvalidFirmwareUpdateException();
        }
        try {
            return jobRepository.saveAndFlush(new FirmwareUpdateJobEntity(
                    deviceId, release, device.getFirmwareVersion(), clock.instant()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidFirmwareUpdateException();
        }
    }

    @Transactional(readOnly = true)
    public List<FirmwareUpdateJobEntity> listJobs(UUID deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new FirmwareUpdateNotFoundException();
        }
        return jobRepository.findTop20ByDeviceIdOrderByCreatedAtDesc(deviceId);
    }

    public void dispatchReadyJobs() {
        List<FirmwareUpdateJobEntity> candidates = transactionTemplate.execute(status -> List.copyOf(
                jobRepository.findTop10ByStatusOrderByCreatedAtAsc(FirmwareUpdateStatus.READY)
        ));
        if (candidates == null) {
            return;
        }
        for (FirmwareUpdateJobEntity candidate : candidates) {
            DeviceEntity device = deviceRepository.findById(candidate.getDeviceId()).orElse(null);
            if (device == null || !device.isApplicationOtaSupported() ||
                    !commandGateway.isConnected(candidate.getDeviceId())) {
                continue;
            }
            String commandId = UUID.randomUUID().toString();
            DispatchCommand command = transactionTemplate.execute(status -> jobRepository
                    .findByIdForUpdate(candidate.getId())
                    .filter(job -> job.getStatus() == FirmwareUpdateStatus.READY)
                    .flatMap(job -> releaseRepository.findById(job.getReleaseId()).map(release -> {
                        job.markInstalling(commandId, clock.instant());
                        return new DispatchCommand(
                                job.getId(), job.getDeviceId(), release.getVersion(),
                                release.getArtifactSha256(), release.getArtifactSize(), commandId
                        );
                    }))
                    .orElse(null));
            if (command == null) {
                continue;
            }
            boolean sent = commandGateway.installFirmware(
                    command.deviceId(), command.jobId(), command.version(), command.sha256(),
                    command.artifactSize(), command.commandId()
            );
            if (!sent) {
                transactionTemplate.executeWithoutResult(status -> jobRepository.findById(command.jobId())
                        .filter(job -> job.getStatus() == FirmwareUpdateStatus.INSTALLING &&
                                command.commandId().equals(job.getCommandId()) && job.getCommandAccepted() == null)
                        .ifPresent(job -> job.returnToReady(clock.instant())));
            }
        }
    }

    @Transactional
    public boolean recordCommandAcknowledgement(UUID deviceId, String commandId, boolean accepted) {
        FirmwareUpdateJobEntity job = jobRepository.findByCommandId(commandId).orElse(null);
        if (job == null || !job.getDeviceId().equals(deviceId) ||
                job.getStatus() != FirmwareUpdateStatus.INSTALLING) {
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
    public void record(UUID deviceId, UUID jobId, String status, String version, String sha256) {
        FirmwareUpdateJobEntity job = jobRepository.findByIdAndDeviceId(jobId, deviceId).orElse(null);
        if (job == null || job.getStatus() != FirmwareUpdateStatus.INSTALLING ||
                !job.getTargetVersion().equals(version)) {
            return;
        }
        FirmwareReleaseEntity release = releaseRepository.findById(job.getReleaseId()).orElse(null);
        if (release == null || !release.getArtifactSha256().equals(sha256)) {
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
        FirmwareUpdateJobEntity job = jobRepository.findByIdAndDeviceId(jobId, deviceId)
                .orElseThrow(FirmwareUpdateNotFoundException::new);
        if (job.getStatus() != FirmwareUpdateStatus.READY &&
                job.getStatus() != FirmwareUpdateStatus.INSTALLING) {
            throw new FirmwareUpdateNotFoundException();
        }
        FirmwareReleaseEntity release = releaseRepository.findById(job.getReleaseId())
                .orElseThrow(FirmwareUpdateNotFoundException::new);
        byte[] artifact = release.getArtifact();
        if (artifact.length != release.getArtifactSize() ||
                !FirmwareArtifactValidator.sha256(artifact).equals(release.getArtifactSha256())) {
            throw new FirmwareUpdateNotFoundException();
        }
        return artifact;
    }

    public void recoverStaleInstalls() {
        Instant staleBefore = clock.instant().minus(STALE_INSTALL_AGE);
        transactionTemplate.executeWithoutResult(status -> jobRepository
                .findAllByStatusAndUpdatedAtBefore(FirmwareUpdateStatus.INSTALLING, staleBefore)
                .forEach(job -> {
                    if (Boolean.TRUE.equals(job.getCommandAccepted())) {
                        job.markFailed("device_health_timeout", clock.instant());
                    } else {
                        job.returnToReady(clock.instant());
                    }
                }));
    }

    public long pendingJobCount() {
        return jobRepository.countByStatusIn(ACTIVE_STATUSES);
    }

    private record DispatchCommand(
            UUID jobId,
            UUID deviceId,
            String version,
            String sha256,
            int artifactSize,
            String commandId
    ) {
    }
}
