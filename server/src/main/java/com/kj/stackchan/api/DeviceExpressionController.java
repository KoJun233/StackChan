package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.expression.DeviceExpressionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping(path = "/api/v1/devices/{deviceId}/expression", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceExpressionController {
    private final DeviceExpressionService expressionService;

    public DeviceExpressionController(DeviceExpressionService expressionService) {
        this.expressionService = expressionService;
    }

    @GetMapping(path = "/frame-rate")
    public DeviceExpressionService.FrameRateSettings frameRate(@PathVariable UUID deviceId) {
        DeviceExpressionService.FrameRateSettings settings = expressionService.getFrameRateSettings(deviceId);
        if (settings == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return settings;
    }

    @PutMapping(path = "/frame-rate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeviceExpressionService.FrameRateSettings configureFrameRate(
            @PathVariable UUID deviceId, @Valid @RequestBody FrameRateRequest request) {
        try {
            DeviceExpressionService.FrameRateSettings settings = expressionService.configureFrameRate(
                    deviceId, request.mode(), request.minFps(), request.maxFps());
            if (settings == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return settings;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping(path = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PreviewResponse preview(@PathVariable UUID deviceId, @Valid @RequestBody PreviewRequest request) {
        try {
            if (!expressionService.preview(deviceId, request.category(), request.value(),
                    request.durationSeconds())) throw new DeviceOfflineException();
            return new PreviewResponse(true);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public record FrameRateRequest(@NotBlank String mode,
                                   @Min(1) @Max(60) int minFps,
                                   @Min(1) @Max(60) int maxFps) {}
    public record PreviewRequest(@NotBlank String category, @NotBlank String value,
                                 @Min(1) @Max(15) int durationSeconds) {}
    public record PreviewResponse(boolean accepted) {}
}
