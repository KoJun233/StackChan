package com.kj.stackchan.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.firmwareupdate.FirmwareReleaseEntity;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateJobEntity;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateService;
import com.kj.stackchan.firmwareupdate.InvalidFirmwareUpdateException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/v1/firmware", produces = MediaType.APPLICATION_JSON_VALUE)
public class FirmwareUpdateController {

    private final FirmwareUpdateService service;

    public FirmwareUpdateController(FirmwareUpdateService service) {
        this.service = service;
    }

    @PostMapping(path = "/releases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FirmwareReleaseResponse importRelease(
            @RequestPart("artifact") MultipartFile artifact,
            @RequestParam String version
    ) {
        try {
            return releaseResponse(service.importRelease(artifact.getBytes(), version));
        } catch (IOException exception) {
            throw new InvalidFirmwareUpdateException();
        }
    }

    @GetMapping("/releases")
    public FirmwareReleaseListResponse listReleases() {
        return new FirmwareReleaseListResponse(
                service.listReleases().stream().map(this::releaseResponse).toList()
        );
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FirmwareUpdateJobResponse createJob(@Valid @RequestBody CreateFirmwareUpdateJobRequest request) {
        return jobResponse(service.createJob(
                request.deviceId(), request.releaseId(), request.confirmedCurrentVersion()
        ));
    }

    @GetMapping("/jobs")
    public FirmwareUpdateJobListResponse listJobs(@RequestParam UUID deviceId) {
        return new FirmwareUpdateJobListResponse(
                service.listJobs(deviceId).stream().map(this::jobResponse).toList()
        );
    }

    private FirmwareReleaseResponse releaseResponse(FirmwareReleaseEntity release) {
        return new FirmwareReleaseResponse(
                release.getId(), release.getVersion(), release.getProjectName(),
                release.getArtifactSha256(), release.getArtifactSize(), release.getCreatedAt()
        );
    }

    private FirmwareUpdateJobResponse jobResponse(FirmwareUpdateJobEntity job) {
        return new FirmwareUpdateJobResponse(
                job.getId(), job.getDeviceId(), job.getReleaseId(), job.getFromVersion(),
                job.getTargetVersion(), job.getStatus().name(), job.getFailureCode(),
                job.getCreatedAt(), job.getUpdatedAt(), job.getCompletedAt()
        );
    }

    public record CreateFirmwareUpdateJobRequest(
            @NotNull UUID deviceId,
            @NotNull UUID releaseId,
            @NotBlank @Size(max = 80) String confirmedCurrentVersion
    ) {
    }

    public record FirmwareReleaseListResponse(List<FirmwareReleaseResponse> releases) {
    }

    public record FirmwareReleaseResponse(
            UUID id,
            String version,
            String projectName,
            String artifactSha256,
            int artifactSize,
            Instant createdAt
    ) {
    }

    public record FirmwareUpdateJobListResponse(List<FirmwareUpdateJobResponse> jobs) {
    }

    public record FirmwareUpdateJobResponse(
            UUID id,
            UUID deviceId,
            UUID releaseId,
            String fromVersion,
            String targetVersion,
            String status,
            String failureCode,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
    }
}
