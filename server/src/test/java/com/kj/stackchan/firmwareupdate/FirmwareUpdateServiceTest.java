package com.kj.stackchan.firmwareupdate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmwareUpdateServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");
    private static final UUID DEVICE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock private FirmwareReleaseRepository releaseRepository;
    @Mock private FirmwareUpdateJobRepository jobRepository;
    @Mock private FirmwareArtifactValidator artifactValidator;
    @Mock private DeviceRepository deviceRepository;
    @Mock private DeviceCommandGateway commandGateway;
    @Mock private PlatformTransactionManager transactionManager;

    @BeforeEach
    void configureTransactions() {
        lenient().when(transactionManager.getTransaction(any()))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void requiresOnlineOtaCapabilityAndExactCurrentVersionConfirmation() {
        DeviceEntity device = device("old-version", true);
        FirmwareReleaseEntity release = release("new-version");
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(releaseRepository.findById(release.getId())).thenReturn(Optional.of(release));
        when(commandGateway.isConnected(DEVICE_ID)).thenReturn(true);
        when(jobRepository.existsByDeviceIdAndStatusIn(eq(DEVICE_ID), any())).thenReturn(false);
        when(jobRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FirmwareUpdateJobEntity job = service().createJob(DEVICE_ID, release.getId(), "old-version");

        assertThat(job.getFromVersion()).isEqualTo("old-version");
        assertThat(job.getTargetVersion()).isEqualTo("new-version");
        assertThat(job.getStatus()).isEqualTo(FirmwareUpdateStatus.READY);

        assertThatThrownBy(() -> service().createJob(DEVICE_ID, release.getId(), "wrong-version"))
                .isInstanceOf(InvalidFirmwareUpdateException.class);
    }

    @Test
    void dispatchesAndCompletesOnlyAfterMatchingBootHealthReport() {
        DeviceEntity device = device("old-version", true);
        FirmwareReleaseEntity release = release("new-version");
        FirmwareUpdateJobEntity job = new FirmwareUpdateJobEntity(
                DEVICE_ID, release, "old-version", NOW
        );
        when(jobRepository.findTop10ByStatusOrderByCreatedAtAsc(FirmwareUpdateStatus.READY))
                .thenReturn(List.of(job));
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(commandGateway.isConnected(DEVICE_ID)).thenReturn(true);
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(releaseRepository.findById(release.getId())).thenReturn(Optional.of(release));
        when(commandGateway.installFirmware(
                eq(DEVICE_ID), eq(job.getId()), eq("new-version"), eq(SHA256), eq(512), anyString()))
                .thenReturn(true);

        FirmwareUpdateService service = service();
        service.dispatchReadyJobs();

        assertThat(job.getStatus()).isEqualTo(FirmwareUpdateStatus.INSTALLING);
        when(jobRepository.findByCommandId(job.getCommandId())).thenReturn(Optional.of(job));
        assertThat(service.recordCommandAcknowledgement(DEVICE_ID, job.getCommandId(), true)).isTrue();
        when(jobRepository.findByIdAndDeviceId(job.getId(), DEVICE_ID)).thenReturn(Optional.of(job));

        service.record(DEVICE_ID, job.getId(), "INSTALLED", "new-version", SHA256);

        assertThat(job.getStatus()).isEqualTo(FirmwareUpdateStatus.INSTALLED);
        verify(commandGateway).installFirmware(
                eq(DEVICE_ID), eq(job.getId()), eq("new-version"), eq(SHA256), eq(512), anyString());
    }

    private FirmwareUpdateService service() {
        return new FirmwareUpdateService(
                releaseRepository, jobRepository, artifactValidator, deviceRepository,
                commandGateway, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager
        );
    }

    private FirmwareReleaseEntity release(String version) {
        return new FirmwareReleaseEntity(
                new ValidatedFirmwareArtifact(version, "stackchan_firmware", SHA256, new byte[512]), NOW
        );
    }

    private DeviceEntity device(String version, boolean otaSupported) {
        DeviceEntity device = mock(DeviceEntity.class);
        lenient().when(device.getFirmwareVersion()).thenReturn(version);
        lenient().when(device.isApplicationOtaSupported()).thenReturn(otaSupported);
        return device;
    }
}
