package com.kj.stackchan.weather;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workday_weather_states")
public class WorkdayWeatherStateEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "location_name", nullable = false, length = 120)
    private String locationName;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "zone_id", nullable = false, length = 80)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WorkdayWeatherStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_code", length = 32)
    private WorkdayWeatherFailureCode lastFailureCode;

    @Column(name = "last_attempted_at", nullable = false)
    private Instant lastAttemptedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "cache_expires_at")
    private Instant cacheExpiresAt;

    @Column(name = "current_observed_at")
    private Instant currentObservedAt;

    @Column(name = "current_temperature")
    private Double currentTemperature;

    @Column(name = "current_apparent_temperature")
    private Double currentApparentTemperature;

    @Column(name = "current_precipitation")
    private Double currentPrecipitation;

    @Column(name = "current_weather_code")
    private Integer currentWeatherCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkdayWeatherStateEntity() {
    }

    public WorkdayWeatherStateEntity(
            UUID deviceId,
            String locationName,
            double latitude,
            double longitude,
            String zoneId,
            Instant now
    ) {
        this.deviceId = deviceId;
        updateLocation(locationName, latitude, longitude, zoneId);
        this.status = WorkdayWeatherStatus.ERROR;
        this.lastFailureCode = WorkdayWeatherFailureCode.REQUEST_FAILED;
        this.lastAttemptedAt = now;
        this.updatedAt = now;
    }

    public void synced(
            String locationName,
            double latitude,
            double longitude,
            String zoneId,
            OpenMeteoClient.CurrentWeather current,
            Instant now,
            Instant expiresAt
    ) {
        updateLocation(locationName, latitude, longitude, zoneId);
        this.status = WorkdayWeatherStatus.READY;
        this.lastFailureCode = null;
        this.lastAttemptedAt = now;
        this.lastSyncedAt = now;
        this.cacheExpiresAt = expiresAt;
        this.currentObservedAt = current.observedAt();
        this.currentTemperature = current.temperature();
        this.currentApparentTemperature = current.apparentTemperature();
        this.currentPrecipitation = current.precipitation();
        this.currentWeatherCode = current.weatherCode();
        this.updatedAt = now;
    }

    public void failed(
            String locationName,
            double latitude,
            double longitude,
            String zoneId,
            WorkdayWeatherFailureCode failureCode,
            Instant now
    ) {
        if (!matches(locationName, latitude, longitude, zoneId)) {
            clearCurrent();
        }
        updateLocation(locationName, latitude, longitude, zoneId);
        this.status = WorkdayWeatherStatus.ERROR;
        this.lastFailureCode = failureCode;
        this.lastAttemptedAt = now;
        this.updatedAt = now;
    }

    public boolean matches(String locationName, double latitude, double longitude, String zoneId) {
        return this.locationName.equals(locationName)
                && Double.compare(this.latitude, latitude) == 0
                && Double.compare(this.longitude, longitude) == 0
                && this.zoneId.equals(zoneId);
    }

    private void updateLocation(String locationName, double latitude, double longitude, String zoneId) {
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoneId = zoneId;
    }

    private void clearCurrent() {
        this.lastSyncedAt = null;
        this.cacheExpiresAt = null;
        this.currentObservedAt = null;
        this.currentTemperature = null;
        this.currentApparentTemperature = null;
        this.currentPrecipitation = null;
        this.currentWeatherCode = null;
    }

    public UUID getDeviceId() { return deviceId; }
    public String getLocationName() { return locationName; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getZoneId() { return zoneId; }
    public WorkdayWeatherStatus getStatus() { return status; }
    public WorkdayWeatherFailureCode getLastFailureCode() { return lastFailureCode; }
    public Instant getLastAttemptedAt() { return lastAttemptedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public Instant getCacheExpiresAt() { return cacheExpiresAt; }
    public Instant getCurrentObservedAt() { return currentObservedAt; }
    public Double getCurrentTemperature() { return currentTemperature; }
    public Double getCurrentApparentTemperature() { return currentApparentTemperature; }
    public Double getCurrentPrecipitation() { return currentPrecipitation; }
    public Integer getCurrentWeatherCode() { return currentWeatherCode; }
    public Instant getUpdatedAt() { return updatedAt; }
}
