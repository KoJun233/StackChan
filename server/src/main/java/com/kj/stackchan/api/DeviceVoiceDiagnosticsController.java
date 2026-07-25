package com.kj.stackchan.api;

import java.util.List;
import java.util.UUID;

import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping(path = "/api/v1/devices", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceVoiceDiagnosticsController {

    private final VoiceTurnDiagnosticsService diagnosticsService;

    public DeviceVoiceDiagnosticsController(VoiceTurnDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/{deviceId}/voice-turns")
    public VoiceTurnListResponse recent(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return new VoiceTurnListResponse(diagnosticsService.recent(deviceId, limit));
    }

    public record VoiceTurnListResponse(List<VoiceTurnDiagnosticsService.VoiceTurnSnapshot> turns) {
    }
}
