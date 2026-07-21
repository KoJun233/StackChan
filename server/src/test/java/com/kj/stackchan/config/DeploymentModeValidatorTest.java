package com.kj.stackchan.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class DeploymentModeValidatorTest {

    @Test
    void acceptsDefaultDevelopmentMode() {
        assertThatCode(() -> validator(false, false).validate()).doesNotThrowAnyException();
    }

    @Test
    void acceptsProductionWithoutLanDevelopment() {
        assertThatCode(() -> validator(true, false).validate()).doesNotThrowAnyException();
    }

    @Test
    void acceptsLanDevelopmentAndWarns(CapturedOutput output) {
        validator(false, true).validate();
        assertThat(output).contains("LAN HTTP development mode active; HTTP traffic and credentials are not protected by TLS");
    }

    @Test
    void rejectsProductionAndLanDevelopmentTogether() {
        assertThatThrownBy(() -> validator(true, true).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("COMPANION_LAN_DEVELOPMENT and COMPANION_PRODUCTION cannot both be true");
    }

    private DeploymentModeValidator validator(boolean production, boolean lanDevelopment) {
        AppProperties properties = new AppProperties();
        properties.setProduction(production);
        properties.setLanDevelopment(lanDevelopment);
        return new DeploymentModeValidator(properties);
    }
}
