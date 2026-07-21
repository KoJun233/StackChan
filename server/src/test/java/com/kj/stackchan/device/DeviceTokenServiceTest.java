package com.kj.stackchan.device;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.kj.stackchan.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    private static final String SECRET = "local-development-device-token-secret-with-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID DEVICE_ID = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SecretKey signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceTokenService deviceTokenService;

    @BeforeEach
    void setUp() {
        deviceTokenService = new DeviceTokenService(properties(), clock, deviceRepository);
    }

    @Test
    void issuesHs256DeviceTokenThatExpiresExactlyTwentyFourHoursLater() {
        DeviceEntity device = new DeviceEntity("stackchan-001", "1.0.0");
        device.rotateCredentials("a".repeat(64), NOW.minusSeconds(1));
        DeviceTokenService.IssuedDeviceToken issued = deviceTokenService.issue(device);

        Jws<Claims> token = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(issued.value());

        assertThat(token.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(token.getPayload().getSubject()).isEqualTo(device.getId().toString());
        assertThat(token.getPayload().get("scope", String.class)).isEqualTo("device");
        assertThat(token.getPayload().get("ver", Long.class)).isEqualTo(1L);
        assertThat(token.getPayload().getExpiration()).isEqualTo(Date.from(NOW.plusSeconds(24 * 60 * 60)));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void issuesAVersionedTokenAndRejectsItAfterCredentialRotation() {
        DeviceEntity device = new DeviceEntity("stackchan-001", "1.0.0");
        device.rotateCredentials("a".repeat(64), NOW.minusSeconds(3));
        device.rotateCredentials("b".repeat(64), NOW.minusSeconds(2));
        device.rotateCredentials("c".repeat(64), NOW.minusSeconds(1));
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        DeviceTokenService.IssuedDeviceToken issued = deviceTokenService.issue(device);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        DeviceTokenService.DeviceToken verified = deviceTokenService.verify(issued.value());
        assertThat(verified.deviceId()).isEqualTo(device.getId());
        assertThat(verified)
                .hasFieldOrPropertyWithValue("credentialVersion", 3L)
                .hasFieldOrPropertyWithValue("expiresAt", NOW.plus(Duration.ofHours(24)));

        device.rotateCredentials("a".repeat(64), NOW.plusSeconds(1));
        assertThatThrownBy(() -> deviceTokenService.verify(issued.value()))
                .isInstanceOf(InvalidDeviceTokenException.class);
    }

    @Test
    void rejectsExpiredAndWrongScopeTokens() {
        String expired = signedToken("device", NOW.minusSeconds(1));
        String expiresAtCurrentTime = signedToken("device", NOW);
        String wrongScope = signedToken("operator", NOW.plusSeconds(60));

        assertThatThrownBy(() -> deviceTokenService.verify(expired))
                .isInstanceOf(InvalidDeviceTokenException.class);
        assertThatThrownBy(() -> deviceTokenService.verify(expiresAtCurrentTime))
                .isInstanceOf(InvalidDeviceTokenException.class);
        assertThatThrownBy(() -> deviceTokenService.verify(wrongScope))
                .isInstanceOf(InvalidDeviceTokenException.class);
        assertThatThrownBy(() -> deviceTokenService.verify("not-a-token"))
                .isInstanceOf(InvalidDeviceTokenException.class);
    }

    @Test
    void rejectsBlankAndMissingSubjects() {
        assertThatThrownBy(() -> deviceTokenService.verify(signedToken(" ", "device", NOW.plusSeconds(60))))
                .isInstanceOf(InvalidDeviceTokenException.class);
        assertThatThrownBy(() -> deviceTokenService.verify(signedToken(null, "device", NOW.plusSeconds(60))))
                .isInstanceOf(InvalidDeviceTokenException.class);
    }

    @Test
    void rejectsAnOtherwiseValidTokenWithoutAnExpiration() {
        JwtBuilder token = Jwts.builder()
                .subject(DEVICE_ID.toString())
                .claim("scope", "device")
                .issuedAt(Date.from(NOW.minusSeconds(60)));

        assertThatThrownBy(() -> deviceTokenService.verify(token.signWith(signingKey, Jwts.SIG.HS256).compact()))
                .isInstanceOf(InvalidDeviceTokenException.class);
    }

    private String signedToken(String scope, Instant expiresAt) {
        return signedToken(DEVICE_ID.toString(), scope, expiresAt);
    }

    private String signedToken(String subject, String scope, Instant expiresAt) {
        JwtBuilder token = Jwts.builder()
                .claim("scope", scope)
                .issuedAt(Date.from(NOW.minusSeconds(60)))
                .expiration(Date.from(expiresAt));
        if (subject != null) {
            token.subject(subject);
        }
        return token.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.setDeviceTokenSecret(SECRET);
        return properties;
    }
}
