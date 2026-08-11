package com.kj.stackchan.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class NotificationRateLimiter {

    static final int REQUESTS_PER_MINUTE = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentMap<UUID, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final Clock clock;

    public NotificationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(UUID integrationId) {
        Instant now = clock.instant();
        Deque<Instant> window = requests.computeIfAbsent(integrationId, ignored -> new ArrayDeque<>());
        synchronized (window) {
            Instant cutoff = now.minus(WINDOW);
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
                window.removeFirst();
            }
            if (window.size() >= REQUESTS_PER_MINUTE) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    public void forget(UUID integrationId) {
        requests.remove(integrationId);
    }
}
