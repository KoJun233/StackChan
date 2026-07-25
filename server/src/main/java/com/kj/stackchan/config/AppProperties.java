package com.kj.stackchan.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "companion")
@Validated
public class AppProperties {

    @Size(min = 32)
    private String deviceTokenSecret;

    private boolean deviceTransportEnabled = true;

    @NotBlank
    private String secretsEncryptionKey;

    private String adminInitialPassword;

    private boolean production;

    private boolean lanDevelopment;

    @NotBlank
    private String wakeModelCatalogDirectory = "/app/wakenet-models";

    public String getDeviceTokenSecret() {
        return deviceTokenSecret;
    }

    public void setDeviceTokenSecret(String deviceTokenSecret) {
        this.deviceTokenSecret = deviceTokenSecret;
    }

    public boolean isDeviceTransportEnabled() {
        return deviceTransportEnabled;
    }

    public void setDeviceTransportEnabled(boolean deviceTransportEnabled) {
        this.deviceTransportEnabled = deviceTransportEnabled;
    }

    public String getSecretsEncryptionKey() {
        return secretsEncryptionKey;
    }

    public void setSecretsEncryptionKey(String secretsEncryptionKey) {
        this.secretsEncryptionKey = secretsEncryptionKey;
    }

    public String getAdminInitialPassword() {
        return adminInitialPassword;
    }

    public void setAdminInitialPassword(String adminInitialPassword) {
        this.adminInitialPassword = adminInitialPassword;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    public boolean isLanDevelopment() {
        return lanDevelopment;
    }

    public void setLanDevelopment(boolean lanDevelopment) {
        this.lanDevelopment = lanDevelopment;
    }

    public String getWakeModelCatalogDirectory() {
        return wakeModelCatalogDirectory;
    }

    public void setWakeModelCatalogDirectory(String wakeModelCatalogDirectory) {
        this.wakeModelCatalogDirectory = wakeModelCatalogDirectory;
    }
}
