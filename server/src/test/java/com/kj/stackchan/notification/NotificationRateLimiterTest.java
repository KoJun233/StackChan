package com.kj.stackchan.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRateLimiterTest {

    @Test
    void allowsThirtyRequestsPerIntegrationAndRejectsTheNextOne() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(Clock.fixed(
                Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC
        ));
        UUID integrationId = UUID.randomUUID();

        for (int index = 0; index < 30; index++) {
            assertThat(limiter.tryAcquire(integrationId)).isTrue();
        }
        assertThat(limiter.tryAcquire(integrationId)).isFalse();
        assertThat(limiter.tryAcquire(UUID.randomUUID())).isTrue();
    }
}
