package com.kj.stackchan.delegation;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Interprets the user's description; a missing fact produces a question before persistence. */
public interface DelegationTaskPlanner {
    TaskPlan plan(PlanningRequest request);

    record PlanningRequest(UUID deviceId, UUID roleId, String description, String zoneId,
                           Instant now, DelegationService.DeliveryMode requestedDeliveryMode) {}

    record TaskPlan(String title, String understanding, DelegationService.ScheduleType scheduleType,
                    List<Integer> daysOfWeek, LocalTime localTime, String zoneId, Instant onceAt,
                    Integer intervalMinutes, DelegationService.DeliveryMode deliveryMode,
                    boolean requiresToolEvidence, List<String> allowedToolNames, List<String> requiredToolNames, String clarification) {}
}
