package com.kj.stackchan.api;

import java.util.UUID;
import com.kj.stackchan.delegation.DelegationService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path="/api/v1",produces=MediaType.APPLICATION_JSON_VALUE)
public class DelegationDeliveryController {
    private final DelegationService service;
    public DelegationDeliveryController(DelegationService service) { this.service=service; }
    @GetMapping("/delegation-push/settings") public ResponseEntity<DelegationService.PushSettingsSnapshot> settings() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pushSettings());
    }
    @PutMapping(path="/delegation-push/settings",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DelegationService.PushSettingsSnapshot> save(@RequestBody DelegationService.PushSettingsInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.savePushSettings(input));
    }
    @PostMapping("/delegation-runs/{id}:retry-delivery") public DelegationService.RunSnapshot retry(@PathVariable UUID id) {
        return service.retryDelivery(id);
    }
}
