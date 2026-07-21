package com.kj.stackchan.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.kj.stackchan.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsTheSingleAdminWithADelegatingPasswordHash() {
        AppProperties properties = propertiesWithInitialPassword("safe-long-password");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("safe-long-password")).thenReturn("{bcrypt}encoded-password");

        service(properties, developmentEnvironment()).bootstrap();

        ArgumentCaptor<AdminUserEntity> savedUser = ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(adminUserRepository).save(savedUser.capture());
        verify(passwordEncoder).encode("safe-long-password");
        assertThat(savedUser.getValue().getUsername()).isEqualTo("admin");
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("{bcrypt}encoded-password");
        assertThat(savedUser.getValue().getCreatedAt()).isEqualTo(Instant.parse("2026-07-17T00:00:00Z"));
    }

    @Test
    void preservesTheExistingAdminOnSubsequentStarts() {
        AppProperties properties = propertiesWithInitialPassword("safe-long-password");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(mock(AdminUserEntity.class)));

        service(properties, developmentEnvironment()).bootstrap();

        verify(adminUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void existingAdminStartsWithoutTheBootstrapPasswordInProduction() {
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(mock(AdminUserEntity.class)));

        service(new AppProperties(), productionEnvironment()).bootstrap();

        verify(adminUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingInitialPasswordOutsideTheDevelopmentProfile() {
        AppProperties properties = new AppProperties();

        assertThatThrownBy(() -> service(properties, productionEnvironment()).bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("COMPANION_ADMIN_INITIAL_PASSWORD is required outside the development profile");
    }

    private AdminBootstrapService service(AppProperties properties, MockEnvironment environment) {
        return new AdminBootstrapService(adminUserRepository, passwordEncoder, properties, CLOCK, environment);
    }

    private AppProperties propertiesWithInitialPassword(String initialPassword) {
        AppProperties properties = new AppProperties();
        properties.setAdminInitialPassword(initialPassword);
        return properties;
    }

    private MockEnvironment developmentEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("development");
        return environment;
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        return environment;
    }
}
