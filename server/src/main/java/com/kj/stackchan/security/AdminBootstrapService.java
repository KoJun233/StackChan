package com.kj.stackchan.security;

import java.time.Clock;
import java.util.Arrays;

import com.kj.stackchan.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrapService implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String MISSING_INITIAL_PASSWORD_MESSAGE =
            "COMPANION_ADMIN_INITIAL_PASSWORD is required outside the development profile";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final Clock clock;
    private final Environment environment;

    public AdminBootstrapService(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties,
            Clock clock,
            Environment environment
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.clock = clock;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    public void bootstrap() {
        if (adminUserRepository.findByUsername(ADMIN_USERNAME).isPresent()) {
            return;
        }

        String initialPassword = appProperties.getAdminInitialPassword();
        if (!StringUtils.hasText(initialPassword)) {
            if (!isDevelopmentProfile()) {
                throw new IllegalStateException(MISSING_INITIAL_PASSWORD_MESSAGE);
            }
            return;
        }

        adminUserRepository.save(
                new AdminUserEntity(ADMIN_USERNAME, passwordEncoder.encode(initialPassword), clock.instant())
        );
    }

    private boolean isDevelopmentProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("development");
    }
}
