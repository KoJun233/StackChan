package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoiceTurnCancellationServiceTest {

    @Test
    void keepsACancelBeforeRegisterAndIsolatesOtherDevices() {
        AdjustableClock clock = new AdjustableClock();
        VoiceTurnCancellationService service = new VoiceTurnCancellationService(clock);
        UUID turnId = UUID.randomUUID();
        UUID cancelledDevice = UUID.randomUUID();

        service.cancel(cancelledDevice, turnId);

        try (var cancelled = service.register(cancelledDevice, turnId);
             var otherDevice = service.register(UUID.randomUUID(), turnId)) {
            assertThatThrownBy(cancelled::throwIfCancelled)
                    .isInstanceOf(VoiceTurnCancelledException.class);
            assertThat(otherDevice.isCancelled()).isFalse();
        }
    }

    @Test
    void completesTheCancellationSignalForAnActiveTurn() {
        VoiceTurnCancellationService service = new VoiceTurnCancellationService(Clock.systemUTC());
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();

        try (var handle = service.register(deviceId, turnId)) {
            service.cancel(deviceId, turnId);

            assertThat(handle.cancellationSignal().block()).isNull();
            assertThat(handle.isCancelled()).isTrue();
        }
    }

    @Test
    void expiresInactiveCancellationMarkers() {
        AdjustableClock clock = new AdjustableClock();
        VoiceTurnCancellationService service = new VoiceTurnCancellationService(clock);
        service.cancel(UUID.randomUUID(), UUID.randomUUID());
        assertThat(service.size()).isOne();

        clock.advance(VoiceTurnCancellationService.RETENTION.plusSeconds(1));
        service.deleteExpired();

        assertThat(service.size()).isZero();
    }

    private static final class AdjustableClock extends Clock {

        private Instant now = Instant.parse("2026-07-26T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
