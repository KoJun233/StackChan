package com.kj.stackchan.weather;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkdayWeatherStateRepository extends JpaRepository<WorkdayWeatherStateEntity, UUID> {
}
