package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class VoiceTurnCancellationService {

    static final Duration RETENTION = Duration.ofMinutes(2);
    static final int MAX_ENTRIES = 128;

    private final Map<TurnKey, CancellationEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public VoiceTurnCancellationService(Clock clock) {
        this.clock = clock;
    }

    public CancellationHandle register(UUID deviceId, UUID turnId) {
        Instant now = clock.instant();
        CancellationEntry entry = entries.compute(new TurnKey(deviceId, turnId), (ignored, existing) -> {
            CancellationEntry resolved = existing == null ? new CancellationEntry(now) : existing;
            resolved.registrations.incrementAndGet();
            resolved.updatedAt = now;
            return resolved;
        });
        trimToBound();
        return new CancellationHandle(new TurnKey(deviceId, turnId), entry);
    }

    public void cancel(UUID deviceId, UUID turnId) {
        Instant now = clock.instant();
        CancellationEntry entry = entries.computeIfAbsent(
                new TurnKey(deviceId, turnId),
                ignored -> new CancellationEntry(now)
        );
        entry.updatedAt = now;
        if (entry.cancelled.compareAndSet(false, true)) {
            entry.signal.tryEmitEmpty();
        }
        trimToBound();
    }

    @Scheduled(fixedDelayString = "PT1M")
    void deleteExpired() {
        Instant cutoff = clock.instant().minus(RETENTION);
        entries.entrySet().removeIf(entry ->
                entry.getValue().registrations.get() == 0 && entry.getValue().updatedAt.isBefore(cutoff)
        );
    }

    int size() {
        return entries.size();
    }

    private void close(TurnKey key, CancellationEntry entry) {
        entry.registrations.decrementAndGet();
        entry.updatedAt = clock.instant();
        if (!entry.cancelled.get()) {
            entries.remove(key, entry);
        }
    }

    private void trimToBound() {
        int overflow = entries.size() - MAX_ENTRIES;
        if (overflow <= 0) {
            return;
        }
        entries.entrySet().stream()
                .filter(entry -> entry.getValue().registrations.get() == 0)
                .sorted(Comparator.comparing(entry -> entry.getValue().updatedAt))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(entries::remove);
    }

    public final class CancellationHandle implements AutoCloseable {

        private final TurnKey key;
        private final CancellationEntry entry;
        private final AtomicBoolean closed = new AtomicBoolean();

        private CancellationHandle(TurnKey key, CancellationEntry entry) {
            this.key = key;
            this.entry = entry;
        }

        public boolean isCancelled() {
            return entry.cancelled.get();
        }

        public void throwIfCancelled() {
            if (isCancelled()) {
                throw new VoiceTurnCancelledException();
            }
        }

        public Mono<Void> cancellationSignal() {
            return entry.signal.asMono();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                VoiceTurnCancellationService.this.close(key, entry);
            }
        }
    }

    private record TurnKey(UUID deviceId, UUID turnId) {
    }

    private static final class CancellationEntry {

        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger registrations = new AtomicInteger();
        private final Sinks.Empty<Void> signal = Sinks.empty();
        private volatile Instant updatedAt;

        private CancellationEntry(Instant now) {
            this.updatedAt = now;
        }
    }
}
