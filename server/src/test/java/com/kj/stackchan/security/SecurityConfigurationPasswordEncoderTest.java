package com.kj.stackchan.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationPasswordEncoderTest {

    private static final String CURRENT_ID = "{pbkdf2@SpringSecurity_v5_8}";

    private final PasswordEncoder passwordEncoder = new SecurityConfiguration().passwordEncoder();

    @Test
    void preservesPasswordEntropyBeyondTheBcryptSeventyTwoByteBoundary() {
        String sharedPrefix = "a".repeat(72);
        String first = sharedPrefix + "x";
        String second = sharedPrefix + "y";

        String encoded = passwordEncoder.encode(first);

        assertThat(first).hasSizeLessThanOrEqualTo(128);
        assertThat(second).hasSizeLessThanOrEqualTo(128);
        assertThat(encoded).startsWith(CURRENT_ID);
        assertThat(passwordEncoder.matches(first, encoded)).isTrue();
        assertThat(passwordEncoder.matches(second, encoded)).isFalse();
    }

    @Test
    void verifiesHistoricalBcryptHashes() {
        String legacyHash = "{bcrypt}" + new BCryptPasswordEncoder().encode("historical-password");

        assertThat(passwordEncoder.matches("historical-password", legacyHash)).isTrue();
    }

    @Test
    void upgradesLegacyIdsButNotCurrentPbkdf2Hashes() {
        String currentHash = passwordEncoder.encode("current-password-123");
        String legacyHash = "{bcrypt}" + new BCryptPasswordEncoder().encode("historical-password");

        assertThat(passwordEncoder.upgradeEncoding(currentHash)).isFalse();
        assertThat(passwordEncoder.upgradeEncoding(legacyHash)).isTrue();
    }
}
