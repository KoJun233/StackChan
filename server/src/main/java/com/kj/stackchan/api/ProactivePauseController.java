package com.kj.stackchan.api;

import java.util.UUID;
import com.kj.stackchan.interaction.ProactivePauseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings/interactions/{deviceId}/roles/{roleId}/proactive-pause")
public class ProactivePauseController {
    private final ProactivePauseService pauses;
    public ProactivePauseController(ProactivePauseService pauses) { this.pauses = pauses; }

    @GetMapping
    public ProactivePauseService.PauseSnapshot get(@PathVariable UUID deviceId, @PathVariable UUID roleId) {
        return pauses.get(deviceId, roleId);
    }

    @PutMapping
    public ProactivePauseService.PauseSnapshot pause(@PathVariable UUID deviceId, @PathVariable UUID roleId,
            @Valid @RequestBody PauseRequest request) {
        return pauses.pause(deviceId, roleId, request.minutes());
    }

    @DeleteMapping
    public ProactivePauseService.PauseSnapshot resume(@PathVariable UUID deviceId, @PathVariable UUID roleId) {
        return pauses.resume(deviceId, roleId);
    }

    public record PauseRequest(@Min(1) @Max(1440) Integer minutes) { }
}
