package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.wakeword.WakeWordModelJobEntity;
import com.kj.stackchan.wakeword.EspSrWakeWordModelCatalog;
import com.kj.stackchan.wakeword.WakeWordModelJobService;
import com.kj.stackchan.wakeword.WakeWordModelOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/wake-word-model-jobs", produces = MediaType.APPLICATION_JSON_VALUE)
public class WakeWordModelController {

    private final WakeWordModelJobService jobService;
    private final EspSrWakeWordModelCatalog catalog;

    public WakeWordModelController(
            WakeWordModelJobService jobService,
            EspSrWakeWordModelCatalog catalog
    ) {
        this.jobService = jobService;
        this.catalog = catalog;
    }

    @GetMapping(path = "/catalog")
    public WakeWordModelCatalogResponse catalog() {
        return new WakeWordModelCatalogResponse(catalog.options());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WakeWordModelJobResponse create(@Valid @RequestBody CreateWakeWordModelJobRequest request) {
        return response(jobService.create(request.deviceId(), request.modelName()));
    }

    @GetMapping
    public WakeWordModelJobListResponse list(@RequestParam UUID deviceId) {
        return new WakeWordModelJobListResponse(jobService.list(deviceId).stream().map(this::response).toList());
    }

    private WakeWordModelJobResponse response(WakeWordModelJobEntity job) {
        return new WakeWordModelJobResponse(
                job.getId(),
                job.getDeviceId(),
                job.getPhrase(),
                job.getStatus().name(),
                job.getModelName(),
                job.getArtifactSha256(),
                job.getArtifactSize(),
                job.getFailureCode(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getInstalledAt()
        );
    }

    public record CreateWakeWordModelJobRequest(
            @NotNull UUID deviceId,
            @NotNull String modelName
    ) {
    }

    public record WakeWordModelCatalogResponse(List<WakeWordModelOption> models) {
    }

    public record WakeWordModelJobListResponse(List<WakeWordModelJobResponse> jobs) {
    }

    public record WakeWordModelJobResponse(
            UUID id,
            UUID deviceId,
            String phrase,
            String status,
            String modelName,
            String artifactSha256,
            Integer artifactSize,
            String failureCode,
            Instant createdAt,
            Instant updatedAt,
            Instant installedAt
    ) {
    }
}
