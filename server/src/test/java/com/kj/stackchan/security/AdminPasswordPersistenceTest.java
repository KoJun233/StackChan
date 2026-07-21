package com.kj.stackchan.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AdminPasswordPersistenceTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsAFullLengthPbkdf2AdministratorPassword() {
        String rawPassword = "a".repeat(127) + "x";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        AdminUserEntity admin = new AdminUserEntity(
                "admin-" + UUID.randomUUID(),
                encodedPassword,
                Instant.parse("2026-07-18T00:00:00Z")
        );

        assertThat(rawPassword).hasSize(128);
        assertThat(encodedPassword)
                .startsWith("{pbkdf2@SpringSecurity_v5_8}")
                .hasSizeGreaterThan(100);

        AdminUserEntity saved = adminUserRepository.saveAndFlush(admin);
        entityManager.clear();
        AdminUserEntity reloaded = adminUserRepository.findById(saved.getId()).orElseThrow();

        assertThat(passwordEncoder.matches(rawPassword, reloaded.getPasswordHash())).isTrue();
    }
}
