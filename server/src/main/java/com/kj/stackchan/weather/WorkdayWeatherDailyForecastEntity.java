package com.kj.stackchan.weather;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workday_weather_daily_forecasts")
public class WorkdayWeatherDailyForecastEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "weather_code", nullable = false)
    private int weatherCode;

    @Column(name = "temperature_max", nullable = false)
    private double temperatureMax;

    @Column(name = "temperature_min", nullable = false)
    private double temperatureMin;

    @Column(name = "apparent_temperature_max", nullable = false)
    private double apparentTemperatureMax;

    @Column(name = "apparent_temperature_min", nullable = false)
    private double apparentTemperatureMin;

    @Column(name = "precipitation_probability_max", nullable = false)
    private int precipitationProbabilityMax;

    @Column(name = "precipitation_sum", nullable = false)
    private double precipitationSum;

    protected WorkdayWeatherDailyForecastEntity() {
    }

    public WorkdayWeatherDailyForecastEntity(UUID id, UUID deviceId, OpenMeteoClient.DailyForecast forecast) {
        this.id = id;
        this.deviceId = deviceId;
        this.forecastDate = forecast.date();
        this.weatherCode = forecast.weatherCode();
        this.temperatureMax = forecast.temperatureMax();
        this.temperatureMin = forecast.temperatureMin();
        this.apparentTemperatureMax = forecast.apparentTemperatureMax();
        this.apparentTemperatureMin = forecast.apparentTemperatureMin();
        this.precipitationProbabilityMax = forecast.precipitationProbabilityMax();
        this.precipitationSum = forecast.precipitationSum();
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public LocalDate getForecastDate() { return forecastDate; }
    public int getWeatherCode() { return weatherCode; }
    public double getTemperatureMax() { return temperatureMax; }
    public double getTemperatureMin() { return temperatureMin; }
    public double getApparentTemperatureMax() { return apparentTemperatureMax; }
    public double getApparentTemperatureMin() { return apparentTemperatureMin; }
    public int getPrecipitationProbabilityMax() { return precipitationProbabilityMax; }
    public double getPrecipitationSum() { return precipitationSum; }
}
