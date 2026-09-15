package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmedReminderChangeTest {
    @Test
    void replaySourceMustStillBeTheSameHeardOccurrence() {
        Instant now = Instant.parse("2026-09-14T08:00:00Z");
        UUID device = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var repository = mock(ReminderRepository.class);
        var service = new ReminderService(repository, null, Clock.fixed(now, ZoneOffset.UTC), new ReminderScheduleCalculator(), null);
        var source = new ReminderEntity(role, device, "喝水", now.minusSeconds(60), "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.USER, now.minusSeconds(120));
        Instant playedAt = now.minusSeconds(30);
        source.completeOccurrence(ReminderStatus.DELIVERED, now.plusSeconds(3600), playedAt);
        when(repository.findByIdAndSourceForUpdate(source.getId(), ReminderSource.USER)).thenReturn(Optional.of(source));
        assertThat(service.requireHeardUserReminder(source.getId(), device, role, playedAt, "喝水").id()).isEqualTo(source.getId());
        assertThatThrownBy(() -> service.requireHeardUserReminder(source.getId(), device, UUID.randomUUID(), playedAt, "喝水"))
                .isInstanceOf(InvalidReminderException.class);
        assertThatThrownBy(() -> service.requireHeardUserReminder(source.getId(), device, role, playedAt.minusSeconds(1), "喝水"))
                .isInstanceOf(InvalidReminderException.class);
        assertThatThrownBy(() -> service.requireHeardUserReminder(source.getId(), device, role, playedAt, "其他内容"))
                .isInstanceOf(InvalidReminderException.class);
        source.markCancelled(now);
        assertThatThrownBy(() -> service.requireHeardUserReminder(source.getId(), device, role, playedAt, "喝水"))
                .isInstanceOf(InvalidReminderException.class);
    }

    @Test
    void refusesChangedOccurrenceDifferentPartnerAndLegacyUnboundProposal() {
        Instant now = Instant.parse("2026-09-14T08:00:00Z");
        UUID device = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var repository = mock(ReminderRepository.class);
        var service = new ReminderService(repository, null, Clock.fixed(now, ZoneOffset.UTC),
                new ReminderScheduleCalculator(), null);
        var reminder = new ReminderEntity(role, device, "喝水", now.plusSeconds(120), "Asia/Shanghai",
                ReminderRecurrence.NONE, 1, null, ReminderSource.USER, now);
        when(repository.findByIdAndSourceForUpdate(reminder.getId(), ReminderSource.USER)).thenReturn(Optional.of(reminder));
        assertThatThrownBy(() -> service.applyConfirmedVoiceChange(null, device, role, now, "喝水", 10))
                .isInstanceOf(InvalidReminderException.class);
        assertThatThrownBy(() -> service.applyConfirmedVoiceChange(reminder.getId(), device, UUID.randomUUID(),
                reminder.getScheduledAt(), "喝水", 10)).isInstanceOf(InvalidReminderException.class);
        Instant original = reminder.getScheduledAt();
        reminder.deferUntil(original.plusSeconds(60), now);
        assertThatThrownBy(() -> service.applyConfirmedVoiceChange(reminder.getId(), device, role, original, "喝水", 10))
                .isInstanceOf(InvalidReminderException.class);
        assertThatThrownBy(() -> service.applyConfirmedVoiceChange(reminder.getId(), device, role,
                reminder.getScheduledAt(), "其他内容", 10)).isInstanceOf(InvalidReminderException.class);
        verify(repository, never()).findById(reminder.getId());
        Instant beforeSnooze = reminder.getScheduledAt();
        var result = service.applyConfirmedVoiceChange(reminder.getId(), device, role, reminder.getScheduledAt(), "喝水", 10);
        assertThat(result.scheduledAt()).isEqualTo(beforeSnooze.plusSeconds(600));
        reminder.markCancelled(now);
        assertThatThrownBy(() -> service.applyConfirmedVoiceChange(reminder.getId(), device, role,
                reminder.getScheduledAt(), "喝水", null)).isInstanceOf(InvalidReminderException.class);
    }
}
