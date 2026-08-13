package com.kj.stackchan.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.ObjectProvider;

@RestController
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping(path = "/api/v1/devices", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(90);

    private final DeviceRepository deviceRepository;
    private final DeviceCommandGateway deviceCommandGateway;
    private final Clock clock;
    private final CompanionRoleService roleService;

    public DeviceController(
            DeviceRepository deviceRepository,
            DeviceCommandGateway deviceCommandGateway,
            Clock clock,
            ObjectProvider<CompanionRoleService> roleService
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandGateway = deviceCommandGateway;
        this.clock = clock;
        this.roleService = roleService.getIfAvailable();
    }

    @GetMapping
    public DeviceListResponse list() {
        List<DeviceResponse> devices = deviceRepository.findAll().stream()
                .sorted(Comparator.comparing(DeviceEntity::getDisplayName)
                        .thenComparing(DeviceEntity::getId))
                .map(device -> new DeviceResponse(
                        device.getId(),
                        device.getDisplayName(),
                        device.getFirmwareVersion(),
                        device.getSafetyState(),
                        device.getRssi(),
                        device.isApplicationOtaSupported(),
                        device.getLastSeenAt(),
                        isOnline(device),
                        deviceCommandGateway.isConnected(device.getId())
                ))
                .toList();
        return new DeviceListResponse(devices);
    }

    @PostMapping(path = "/{deviceId}/commands/stop-motion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void stopMotion(@PathVariable UUID deviceId) {
        if (!deviceCommandGateway.stopMotion(deviceId)) {
            throw new DeviceOfflineException();
        }
    }

    @GetMapping(path = "/{deviceId}/active-role")
    public CompanionRoleService.RoleSnapshot activeRole(@PathVariable UUID deviceId) {
        if (roleService == null) throw new IllegalStateException("Role service is unavailable");
        return roleService.getActive(deviceId);
    }

    @PutMapping(path = "/{deviceId}/active-role", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompanionRoleService.RoleSnapshot switchRole(
            @PathVariable UUID deviceId,
            @Valid @RequestBody ActiveRoleRequest request
    ) {
        if (roleService == null) throw new IllegalStateException("Role service is unavailable");
        return roleService.switchActive(deviceId, request.roleId());
    }

    public record DeviceListResponse(List<DeviceResponse> devices) {
    }

    private boolean isOnline(DeviceEntity device) {
        Instant lastSeenAt = device.getLastSeenAt();
        return lastSeenAt != null && !lastSeenAt.isBefore(clock.instant().minus(ONLINE_WINDOW));
    }

    public record DeviceResponse(
            UUID id,
            String displayName,
            String firmwareVersion,
            String safetyState,
            Integer rssi,
            boolean applicationOtaSupported,
            Instant lastSeenAt,
            boolean online,
            boolean commandAvailable
    ) {
    }

    public record ActiveRoleRequest(@NotNull UUID roleId) {}
}
