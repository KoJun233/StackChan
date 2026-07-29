package com.kj.stackchan.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @Valid
    private Agent agent = new Agent();

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

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public static class Agent {

        private boolean enabled = true;

        @Min(1)
        @Max(8)
        private int maxToolCalls = 4;

        @NotNull
        private Duration timeout = Duration.ofSeconds(20);

        @NotBlank
        private String userZoneId = "Asia/Shanghai";

        @Min(1024)
        @Max(65536)
        private int maxToolResultBytes = 8192;

        @Min(1024)
        @Max(131072)
        private int maxTotalToolResultBytes = 24576;

        @NotBlank
        private String skillsDirectory = "./data/agent-skills";

        @Min(65536)
        @Max(16777216)
        private int maxSkillArchiveBytes = 4194304;

        @Min(65536)
        @Max(67108864)
        private int maxSkillUncompressedBytes = 16777216;

        @Min(1)
        @Max(1024)
        private int maxSkillFileCount = 256;

        @Min(1024)
        @Max(16777216)
        private int maxSkillFileBytes = 2097152;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        @AssertTrue(message = "agent timeout must be between 1 and 60 seconds")
        public boolean isTimeoutInAllowedRange() {
            return timeout != null
                    && timeout.compareTo(Duration.ofSeconds(1)) >= 0
                    && timeout.compareTo(Duration.ofSeconds(60)) <= 0;
        }

        public String getUserZoneId() {
            return userZoneId;
        }

        public void setUserZoneId(String userZoneId) {
            this.userZoneId = userZoneId;
        }

        public int getMaxToolResultBytes() {
            return maxToolResultBytes;
        }

        public void setMaxToolResultBytes(int maxToolResultBytes) {
            this.maxToolResultBytes = maxToolResultBytes;
        }

        public int getMaxTotalToolResultBytes() {
            return maxTotalToolResultBytes;
        }

        public void setMaxTotalToolResultBytes(int maxTotalToolResultBytes) {
            this.maxTotalToolResultBytes = maxTotalToolResultBytes;
        }

        public String getSkillsDirectory() {
            return skillsDirectory;
        }

        public void setSkillsDirectory(String skillsDirectory) {
            this.skillsDirectory = skillsDirectory;
        }

        public int getMaxSkillArchiveBytes() {
            return maxSkillArchiveBytes;
        }

        public void setMaxSkillArchiveBytes(int maxSkillArchiveBytes) {
            this.maxSkillArchiveBytes = maxSkillArchiveBytes;
        }

        public int getMaxSkillUncompressedBytes() {
            return maxSkillUncompressedBytes;
        }

        public void setMaxSkillUncompressedBytes(int maxSkillUncompressedBytes) {
            this.maxSkillUncompressedBytes = maxSkillUncompressedBytes;
        }

        public int getMaxSkillFileCount() {
            return maxSkillFileCount;
        }

        public void setMaxSkillFileCount(int maxSkillFileCount) {
            this.maxSkillFileCount = maxSkillFileCount;
        }

        public int getMaxSkillFileBytes() {
            return maxSkillFileBytes;
        }

        public void setMaxSkillFileBytes(int maxSkillFileBytes) {
            this.maxSkillFileBytes = maxSkillFileBytes;
        }
    }
}
