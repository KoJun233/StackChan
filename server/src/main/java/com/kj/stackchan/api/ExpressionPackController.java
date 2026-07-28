package com.kj.stackchan.api;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.expression.DeviceExpressionPackEntity;
import com.kj.stackchan.expression.ExpressionPackEntity;
import com.kj.stackchan.expression.ExpressionPackService;
import com.kj.stackchan.expression.ExpressionPackStateEntity;
import com.kj.stackchan.expression.ExpressionState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping(path = "/api/v1/expression-packs", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExpressionPackController {

    private final ExpressionPackService service;

    public ExpressionPackController(ExpressionPackService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExpressionPackResponse create(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            MultipartHttpServletRequest request
    ) {
        EnumMap<ExpressionState, byte[]> images = new EnumMap<>(ExpressionState.class);
        try {
            for (ExpressionState state : ExpressionState.values()) {
                MultipartFile file = request.getFile(state.wireName());
                images.put(state, file == null ? null : file.getBytes());
            }
        } catch (Exception exception) {
            throw new com.kj.stackchan.expression.InvalidExpressionPackException();
        }
        return response(service.create(name, description, images));
    }

    @GetMapping
    public ExpressionPackListResponse list() {
        return new ExpressionPackListResponse(service.list().stream().map(this::response).toList());
    }

    @GetMapping(path = "/{packId}/states/{stateName}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> state(@PathVariable UUID packId, @PathVariable String stateName) {
        ExpressionPackStateEntity state = service.state(packId, stateName);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(state.getImageSize())
                .cacheControl(CacheControl.noStore())
                .body(state.getImageData());
    }

    @GetMapping(path = "/device")
    public DeviceExpressionPackResponse device(@RequestParam UUID deviceId) {
        return deviceResponse(service.deviceSelection(deviceId));
    }

    @PostMapping(path = "/{packId}/activate")
    public DeviceExpressionPackResponse activate(
            @PathVariable UUID packId,
            @Valid @RequestBody DeviceExpressionPackRequest request
    ) {
        return deviceResponse(service.activate(request.deviceId(), packId));
    }

    @PostMapping(path = "/deactivate")
    public DeviceExpressionPackResponse deactivate(@Valid @RequestBody DeviceExpressionPackRequest request) {
        return deviceResponse(service.deactivate(request.deviceId()));
    }

    @DeleteMapping(path = "/{packId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID packId) {
        service.delete(packId);
    }

    private ExpressionPackResponse response(ExpressionPackEntity pack) {
        return new ExpressionPackResponse(
                pack.getId(),
                pack.getName(),
                pack.getDescription(),
                pack.getFormatVersion(),
                pack.getArtifactSha256(),
                pack.getArtifactSize(),
                List.of(ExpressionState.values()).stream().map(ExpressionState::wireName).toList(),
                pack.getCreatedAt()
        );
    }

    private DeviceExpressionPackResponse deviceResponse(DeviceExpressionPackEntity mapping) {
        return new DeviceExpressionPackResponse(
                mapping.getDeviceId(),
                mapping.getPackId(),
                mapping.isEnabled(),
                mapping.getStatus().name(),
                mapping.getFailureCode(),
                mapping.getUpdatedAt(),
                mapping.getInstalledAt()
        );
    }

    public record DeviceExpressionPackRequest(@NotNull UUID deviceId) {
    }

    public record ExpressionPackListResponse(List<ExpressionPackResponse> packs) {
    }

    public record ExpressionPackResponse(
            UUID id,
            String name,
            String description,
            int formatVersion,
            String artifactSha256,
            int artifactSize,
            List<String> states,
            java.time.Instant createdAt
    ) {
    }

    public record DeviceExpressionPackResponse(
            UUID deviceId,
            UUID packId,
            boolean enabled,
            String status,
            String failureCode,
            java.time.Instant updatedAt,
            java.time.Instant installedAt
    ) {
    }
}
