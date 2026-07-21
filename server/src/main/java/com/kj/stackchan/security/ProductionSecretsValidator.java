package com.kj.stackchan.security;

import com.kj.stackchan.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSecretsValidator implements ApplicationRunner {

    private static final String DEVELOPMENT_DEVICE_TOKEN_SECRET =
            "local-development-device-token-secret-with-at-least-32-bytes";
    private static final String DEVELOPMENT_SECRETS_ENCRYPTION_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final AppProperties appProperties;

    public ProductionSecretsValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    public void validate() {
        if (!appProperties.isProduction()) {
            return;
        }
        if (DEVELOPMENT_DEVICE_TOKEN_SECRET.equals(appProperties.getDeviceTokenSecret())) {
            throw new IllegalStateException(
                    "COMPANION_DEVICE_TOKEN_SECRET must not use the development default in production"
            );
        }
        if (DEVELOPMENT_SECRETS_ENCRYPTION_KEY.equals(appProperties.getSecretsEncryptionKey())) {
            throw new IllegalStateException(
                    "COMPANION_SECRETS_ENCRYPTION_KEY must not use the development default in production"
            );
        }
    }
}
