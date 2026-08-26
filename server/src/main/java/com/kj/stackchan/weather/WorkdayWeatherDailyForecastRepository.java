package com.kj.stackchan.weather;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkdayWeatherDailyForecastRepository
        extends JpaRepository<WorkdayWeatherDailyForecastEntity, UUID> {

    List<WorkdayWeatherDailyForecastEntity> findAllByDeviceIdOrderByForecastDateAsc(UUID deviceId);

    void deleteAllByDeviceId(UUID deviceId);
}
