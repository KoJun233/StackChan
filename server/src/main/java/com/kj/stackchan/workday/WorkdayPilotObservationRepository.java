package com.kj.stackchan.workday;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkdayPilotObservationRepository
        extends JpaRepository<WorkdayPilotObservationEntity, UUID> {
}
