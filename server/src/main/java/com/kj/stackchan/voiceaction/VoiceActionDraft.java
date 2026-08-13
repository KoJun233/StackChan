package com.kj.stackchan.voiceaction;

import java.time.Instant;

public record VoiceActionDraft(
        VoiceActionType actionType,
        boolean confirmationRequired,
        String content,
        String title,
        Instant scheduledAt,
        String zoneId,
        String recurrenceType,
        Integer recurrenceInterval,
        Integer durationMinutes,
        Instant targetAt,
        Integer volumePercent,
        String memoryCategory,
        java.util.UUID targetReference
) {
    public static VoiceActionDraft reminder(String content, Instant scheduledAt, String zoneId,
                                             String recurrenceType, Integer recurrenceInterval) {
        return new VoiceActionDraft(VoiceActionType.CREATE_REMINDER, true, content, null, scheduledAt, zoneId,
                recurrenceType, recurrenceInterval, null, null, null, null, null);
    }

    public static VoiceActionDraft notificationResponse(
            VoiceActionType actionType,
            java.util.UUID notificationId,
            Integer snoozeMinutes
    ) {
        return new VoiceActionDraft(actionType, true, null, null, null, null, null, null,
                snoozeMinutes, null, null, null, notificationId);
    }
}
