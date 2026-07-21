package com.kj.stackchan.security;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasswordServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void storesAnAdaptiveHashWhenTheCurrentPasswordMatches() {
        AdminUserEntity admin = admin();
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("old-password", "{noop}old-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("{bcrypt}new-hash");

        service().changePassword("admin", "old-password", "new-password-123");

        assertThat(admin.getPasswordHash()).isEqualTo("{bcrypt}new-hash");
        verify(passwordEncoder).encode("new-password-123");
    }

    @Test
    void rejectsAWrongCurrentPasswordWithoutChangingTheEntity() {
        AdminUserEntity admin = admin();
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong-password", "{noop}old-password")).thenReturn(false);

        assertThatThrownBy(() -> service().changePassword("admin", "wrong-password", "new-password-123"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(admin.getPasswordHash()).isEqualTo("{noop}old-password");
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    private AdminPasswordService service() {
        return new AdminPasswordService(adminUserRepository, passwordEncoder);
    }

    private AdminUserEntity admin() {
        return new AdminUserEntity("admin", "{noop}old-password", Instant.parse("2026-07-17T00:00:00Z"));
    }
}
