package com.kj.stackchan.security;

import com.kj.stackchan.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsValidatorTest {

    @Test
    void rejectsKnownDevelopmentDeviceTokenSecretInProduction() {
        AppProperties properties = productionProperties();
        properties.setDeviceTokenSecret("local-development-device-token-secret-with-at-least-32-bytes");

        assertThatThrownBy(() -> new ProductionSecretsValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("COMPANION_DEVICE_TOKEN_SECRET must not use the development default in production");
    }

    @Test
    void rejectsKnownDevelopmentEncryptionKeyInProduction() {
        AppProperties properties = productionProperties();
        properties.setSecretsEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        assertThatThrownBy(() -> new ProductionSecretsValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("COMPANION_SECRETS_ENCRYPTION_KEY must not use the development default in production");
    }

    @Test
    void acceptsNonDevelopmentSecretsOutsideProduction() {
        AppProperties properties = new AppProperties();
        properties.setDeviceTokenSecret("a-different-test-device-token-secret-with-at-least-32-bytes");
        properties.setSecretsEncryptionKey("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU2Nzg=");

        assertThatCode(() -> new ProductionSecretsValidator(properties).validate()).doesNotThrowAnyException();
    }

    private AppProperties productionProperties() {
        AppProperties properties = new AppProperties();
        properties.setProduction(true);
        properties.setDeviceTokenSecret("a-different-test-device-token-secret-with-at-least-32-bytes");
        properties.setSecretsEncryptionKey("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU2Nzg=");
        return properties;
    }
}
