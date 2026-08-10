package com.kj.stackchan.health;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class ProviderHealthRegistry {

    private final ConcurrentMap<String, ProviderConnectivity> connectivity = new ConcurrentHashMap<>();
    private final Clock clock;

    public ProviderHealthRegistry(Clock clock) {
        this.clock = clock;
    }

    public void succeeded(String provider) {
        connectivity.put(provider, new ProviderConnectivity("HEALTHY", clock.instant(), null));
    }

    public void failed(String provider) {
        connectivity.put(provider, new ProviderConnectivity("FAILED", clock.instant(), "provider_unavailable"));
    }

    public ProviderConnectivity status(String provider, boolean configured) {
        if (!configured) {
            return new ProviderConnectivity("NOT_CONFIGURED", null, null);
        }
        return connectivity.getOrDefault(provider, new ProviderConnectivity("UNKNOWN", null, null));
    }

    public record ProviderConnectivity(String status, Instant checkedAt, String failureCode) {
    }
}
