package com.kj.stackchan.device;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.kj.stackchan.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceTokenService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final String DEVICE_SCOPE = "device";

    private final Clock clock;
    private final DeviceRepository deviceRepository;
    private final SecretKey signingKey;

    public DeviceTokenService(AppProperties appProperties, Clock clock, DeviceRepository deviceRepository) {
        this.clock = clock;
        this.deviceRepository = deviceRepository;
        String secret = appProperties.getDeviceTokenSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("companion.device-token-secret must not be blank");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("companion.device-token-secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public IssuedDeviceToken issue(DeviceEntity device) {
        Objects.requireNonNull(device, "device");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(TOKEN_TTL);
        String value = Jwts.builder()
                .subject(device.getId().toString())
                .claim("scope", DEVICE_SCOPE)
                .claim("ver", device.getCredentialVersion())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedDeviceToken(value, expiresAt);
    }

    public DeviceToken verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidDeviceTokenException("Device token is required");
        }

        try {
            Jws<Claims> parsedToken = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(parsedToken.getHeader().getAlgorithm())) {
                throw new InvalidDeviceTokenException("Device token must use HS256");
            }
            if (!DEVICE_SCOPE.equals(parsedToken.getPayload().get("scope", String.class))) {
                throw new InvalidDeviceTokenException("Device token has an invalid scope");
            }
            Date expiresAt = parsedToken.getPayload().getExpiration();
            if (expiresAt == null || !expiresAt.toInstant().isAfter(clock.instant())) {
                throw new InvalidDeviceTokenException("Device token expiration is required and must be in the future");
            }
            String subject = parsedToken.getPayload().getSubject();
            if (subject == null || subject.isBlank()) {
                throw new InvalidDeviceTokenException("Device token subject is required");
            }
            Long credentialVersion = parsedToken.getPayload().get("ver", Long.class);
            if (credentialVersion == null) {
                throw new InvalidDeviceTokenException("Device token credential version is required");
            }
            UUID deviceId = UUID.fromString(subject);
            DeviceEntity device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new InvalidDeviceTokenException("Device token is invalid"));
            if (device.getCredentialVersion() != credentialVersion) {
                throw new InvalidDeviceTokenException("Device token is invalid");
            }
            return new DeviceToken(deviceId, credentialVersion, expiresAt.toInstant());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidDeviceTokenException("Device token is invalid", exception);
        }
    }

    public record DeviceToken(UUID deviceId, long credentialVersion, Instant expiresAt) {
    }

    public record IssuedDeviceToken(String value, Instant expiresAt) {
    }
}
