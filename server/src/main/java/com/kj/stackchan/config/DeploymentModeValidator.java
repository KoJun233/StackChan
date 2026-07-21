package com.kj.stackchan.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeploymentModeValidator implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentModeValidator.class);

    private final AppProperties appProperties;

    public DeploymentModeValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        if (appProperties.isProduction() && appProperties.isLanDevelopment()) {
            throw new IllegalStateException(
                    "COMPANION_LAN_DEVELOPMENT and COMPANION_PRODUCTION cannot both be true"
            );
        }
        if (appProperties.isLanDevelopment()) {
            logger.warn("LAN HTTP development mode active; HTTP traffic and credentials are not protected by TLS");
        }
    }
}
