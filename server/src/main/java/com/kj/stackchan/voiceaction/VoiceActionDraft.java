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
        String memoryCategory
) {
    public static VoiceActionDraft reminder(String content, Instant scheduledAt, String zoneId,
                                             String recurrenceType, Integer recurrenceInterval) {
        return new VoiceActionDraft(VoiceActionType.CREATE_REMINDER, true, content, null, scheduledAt, zoneId,
                recurrenceType, recurrenceInterval, null, null, null, null);
    }
}
