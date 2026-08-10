package com.kj.stackchan.device;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "devices")
public class DeviceEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "hardware_id", nullable = false, unique = true)
    private String hardwareId;

    @Column(name = "firmware_version", nullable = false)
    private String firmwareVersion;

    @Column(name = "display_name", nullable = false)
    private String displayName = "StackChan";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "safety_state", nullable = false)
    private String safetyState = "motion_disabled";

    @Column
    private Integer rssi;

    @Column(name = "application_ota_supported", nullable = false)
    private boolean applicationOtaSupported;

    @Column(name = "refresh_token_hash")
    private String refreshTokenHash;

    @Column(name = "refresh_token_issued_at")
    private Instant refreshTokenIssuedAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    protected DeviceEntity() {
    }

    public DeviceEntity(String hardwareId, String firmwareVersion) {
        this.hardwareId = hardwareId;
        this.firmwareVersion = firmwareVersion;
    }

    public UUID getId() {
        return id;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public String getSafetyState() {
        return safetyState;
    }

    public Integer getRssi() {
        return rssi;
    }

    public boolean isApplicationOtaSupported() {
        return applicationOtaSupported;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public Instant getRefreshTokenIssuedAt() {
        return refreshTokenIssuedAt;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    void prepareForRepairing(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
        this.safetyState = "motion_disabled";
        this.applicationOtaSupported = false;
    }

    void rotateCredentials(String refreshTokenHash, Instant issuedAt) {
        this.refreshTokenHash = refreshTokenHash;
        this.refreshTokenIssuedAt = issuedAt;
        this.credentialVersion++;
    }

    void recordHeartbeat(
            Instant lastSeenAt,
            String safetyState,
            String firmwareVersion,
            Integer rssi,
            boolean applicationOtaSupported
    ) {
        this.lastSeenAt = lastSeenAt;
        this.safetyState = safetyState;
        this.rssi = rssi;
        this.applicationOtaSupported = applicationOtaSupported;
        if (firmwareVersion != null) {
            this.firmwareVersion = firmwareVersion;
        }
    }

    void recordHeartbeat(Instant lastSeenAt, String safetyState, String firmwareVersion) {
        recordHeartbeat(lastSeenAt, safetyState, firmwareVersion, null, false);
    }
}
