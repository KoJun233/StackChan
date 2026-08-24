package com.kj.stackchan.api;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.expression.DeviceExpressionPackEntity;
import com.kj.stackchan.expression.ExpressionPackEntity;
import com.kj.stackchan.expression.ExpressionPackClipEntity;
import com.kj.stackchan.expression.ExpressionPackType;
import com.kj.stackchan.expression.LifecycleClip;
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

    @PostMapping(path = "/lifecycle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExpressionPackResponse createLifecycle(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            MultipartHttpServletRequest request
    ) {
        EnumMap<LifecycleClip, byte[]> clips = new EnumMap<>(LifecycleClip.class);
        EnumMap<LifecycleClip, Integer> frameDelays = new EnumMap<>(LifecycleClip.class);
        try {
            for (LifecycleClip clip : LifecycleClip.values()) {
                MultipartFile file = request.getFile(clip.wireName());
                if (file == null || file.isEmpty()) continue;
                clips.put(clip, file.getBytes());
                String delay = request.getParameter(clip.wireName() + "_frame_delay_ms");
                if (delay != null) frameDelays.put(clip, Integer.parseInt(delay));
            }
        } catch (Exception exception) {
            throw new com.kj.stackchan.expression.InvalidExpressionPackException();
        }
        return response(service.createLifecycle(name, description, clips, frameDelays));
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

    @GetMapping(path = "/{packId}/clips/{clipName}", produces = "application/vnd.espressif.eaf")
    public ResponseEntity<byte[]> clip(@PathVariable UUID packId, @PathVariable String clipName) {
        ExpressionPackClipEntity clip = service.clip(packId, clipName);
        byte[] data = clip.getClipData();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.espressif.eaf"))
                .contentLength(data.length)
                .cacheControl(CacheControl.noStore())
                .body(data);
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
        List<ExpressionPackClipResponse> clips = pack.getPackType() == ExpressionPackType.LIFECYCLE_EAF
                ? service.clips(pack.getId()).stream().map(value -> new ExpressionPackClipResponse(
                        value.getClipName(), value.getClipSize(), value.getFrameCount(),
                        value.getFrameDelayMs(), value.getClipSha256())).toList()
                : List.of();
        return new ExpressionPackResponse(
                pack.getId(),
                pack.getName(),
                pack.getDescription(),
                pack.getFormatVersion(),
                pack.getPackType().name(),
                pack.getArtifactSha256(),
                pack.getArtifactSize(),
                pack.getPackType() == ExpressionPackType.STATIC_PNG
                        ? List.of(ExpressionState.values()).stream().map(ExpressionState::wireName).toList()
                        : List.of(),
                clips,
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
            String packType,
            String artifactSha256,
            int artifactSize,
            List<String> states,
            List<ExpressionPackClipResponse> clips,
            java.time.Instant createdAt
    ) {
    }

    public record ExpressionPackClipResponse(
            String name, int size, int frameCount, int frameDelayMs, String sha256
    ) {}

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
