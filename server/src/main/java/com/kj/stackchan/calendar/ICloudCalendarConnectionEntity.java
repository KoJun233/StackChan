package com.kj.stackchan.calendar;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "icloud_calendar_connections")
public class ICloudCalendarConnectionEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "account_email", nullable = false, length = 320)
    private String accountEmail;

    @Column(name = "password_ciphertext", nullable = false)
    private String passwordCiphertext;

    @Column(name = "password_iv", nullable = false, length = 64)
    private String passwordIv;

    @Column(name = "principal_url", length = 2048)
    private String principalUrl;

    @Column(name = "calendar_home_url", length = 2048)
    private String calendarHomeUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ICloudCalendarConnectionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_code", length = 40)
    private ICloudCalendarFailureCode lastFailureCode;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ICloudCalendarConnectionEntity() {
    }

    public ICloudCalendarConnectionEntity(
            UUID deviceId,
            String accountEmail,
            String passwordCiphertext,
            String passwordIv,
            Instant now
    ) {
        this.deviceId = deviceId;
        this.createdAt = now;
        updateCredentials(accountEmail, passwordCiphertext, passwordIv, now);
    }

    public void updateCredentials(
            String accountEmail,
            String passwordCiphertext,
            String passwordIv,
            Instant now
    ) {
        this.accountEmail = accountEmail;
        this.passwordCiphertext = passwordCiphertext;
        this.passwordIv = passwordIv;
        this.status = ICloudCalendarConnectionStatus.CONFIGURED;
        this.lastFailureCode = null;
        this.updatedAt = now;
    }

    public void connected(String principalUrl, String calendarHomeUrl, Instant now) {
        this.principalUrl = principalUrl;
        this.calendarHomeUrl = calendarHomeUrl;
        this.status = ICloudCalendarConnectionStatus.CONNECTED;
        this.lastFailureCode = null;
        this.lastTestedAt = now;
        this.updatedAt = now;
    }

    public void failed(ICloudCalendarFailureCode failureCode, Instant now) {
        this.status = failureCode == ICloudCalendarFailureCode.AUTHENTICATION_FAILED
                ? ICloudCalendarConnectionStatus.AUTH_FAILED
                : ICloudCalendarConnectionStatus.ERROR;
        this.lastFailureCode = failureCode;
        this.lastTestedAt = now;
        this.updatedAt = now;
    }

    public void synced(Instant now) {
        this.status = ICloudCalendarConnectionStatus.CONNECTED;
        this.lastFailureCode = null;
        this.lastSyncedAt = now;
        this.updatedAt = now;
    }

    public UUID getDeviceId() { return deviceId; }
    public String getAccountEmail() { return accountEmail; }
    public String getPasswordCiphertext() { return passwordCiphertext; }
    public String getPasswordIv() { return passwordIv; }
    public String getPrincipalUrl() { return principalUrl; }
    public String getCalendarHomeUrl() { return calendarHomeUrl; }
    public ICloudCalendarConnectionStatus getStatus() { return status; }
    public ICloudCalendarFailureCode getLastFailureCode() { return lastFailureCode; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
