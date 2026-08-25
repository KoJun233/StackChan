package com.kj.stackchan.calendar;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.llm.SecretCipher;
import com.kj.stackchan.workday.WorkdaySettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ICloudCalendarServiceTest {

    @Mock private ICloudCalendarConnectionRepository connectionRepository;
    @Mock private ICloudCalendarRepository calendarRepository;
    @Mock private WorkdayCalendarEventRepository eventRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private WorkdaySettingsService workdaySettingsService;
    @Mock private SecretCipher secretCipher;
    @Mock private ICloudCalDavClient calDavClient;

    @Test
    void encryptsPasswordAndKeepsNewCalendarsDisallowed() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(connectionRepository.findById(deviceId)).thenReturn(Optional.empty());
        when(secretCipher.encrypt("app-password"))
                .thenReturn(new SecretCipher.EncryptedSecret("ciphertext", "initialization-vector"));
        when(calDavClient.discover("me@icloud.com", "app-password")).thenReturn(new ICloudCalDavClient.DiscoveryResult(
                "https://p01-caldav.icloud.com/principal/",
                "https://p01-caldav.icloud.com/calendars/",
                List.of(new ICloudCalDavClient.DiscoveredCalendar(
                        "https://p01-caldav.icloud.com/calendars/work/", "工作"
                ))
        ));
        when(calendarRepository.findAllByDeviceIdOrderByDisplayNameAsc(deviceId)).thenReturn(List.of());
        when(eventRepository.countByDeviceIdAndExpiresAtAfter(deviceId, now)).thenReturn(0L);
        when(eventRepository.findFirstByDeviceIdAndExpiresAtAfterOrderByExpiresAtAsc(deviceId, now))
                .thenReturn(Optional.empty());
        var service = new ICloudCalendarService(
                connectionRepository, calendarRepository, eventRepository, deviceRepository,
                workdaySettingsService, secretCipher, calDavClient, Clock.fixed(now, ZoneOffset.UTC)
        );

        var snapshot = service.connect(
                deviceId,
                new ICloudCalendarService.ConnectionCommand("ME@icloud.com", "app-password")
        );

        ArgumentCaptor<ICloudCalendarConnectionEntity> connectionCaptor =
                ArgumentCaptor.forClass(ICloudCalendarConnectionEntity.class);
        verify(connectionRepository).saveAndFlush(connectionCaptor.capture());
        assertThat(connectionCaptor.getValue().getPasswordCiphertext()).isEqualTo("ciphertext");
        assertThat(connectionCaptor.getValue().getPasswordCiphertext()).doesNotContain("app-password");
        ArgumentCaptor<ICloudCalendarEntity> calendarCaptor = ArgumentCaptor.forClass(ICloudCalendarEntity.class);
        verify(calendarRepository).save(calendarCaptor.capture());
        assertThat(calendarCaptor.getValue().isAllowed()).isFalse();
        assertThat(snapshot.account()).isEqualTo("m***@icloud.com");
        assertThat(snapshot.toString()).doesNotContain("app-password", "ciphertext", "initialization-vector");
    }

    @Test
    void rejectsPhoneBasedAppleAccountIdentifiers() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(connectionRepository.findById(deviceId)).thenReturn(Optional.empty());
        var service = new ICloudCalendarService(
                connectionRepository, calendarRepository, eventRepository, deviceRepository,
                workdaySettingsService, secretCipher, calDavClient, Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.test(
                deviceId,
                new ICloudCalendarService.ConnectionCommand("138 0013-8000", "app-password")
        ))
                .isInstanceOf(InvalidICloudCalendarException.class)
                .hasMessage("Apple Account email is invalid");
        assertThatThrownBy(() -> service.test(
                deviceId,
                new ICloudCalendarService.ConnectionCommand("+8613800138000", "app-password")
        ))
                .isInstanceOf(InvalidICloudCalendarException.class)
                .hasMessage("Apple Account email is invalid");

        verifyNoInteractions(calDavClient);
    }

    @Test
    void rejectsMalformedAppleAccountEmail() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(connectionRepository.findById(deviceId)).thenReturn(Optional.empty());
        var service = new ICloudCalendarService(
                connectionRepository, calendarRepository, eventRepository, deviceRepository,
                workdaySettingsService, secretCipher, calDavClient, Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.test(
                deviceId,
                new ICloudCalendarService.ConnectionCommand("not-an-email", "app-password")
        ))
                .isInstanceOf(InvalidICloudCalendarException.class)
                .hasMessage("Apple Account email is invalid");
        verifyNoInteractions(calDavClient);
    }

    @Test
    void returnsEventsThatOverlapTheRequestedWindow() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T14:55:00Z");
        Instant to = now.plusSeconds(7 * 24 * 60 * 60);
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(eventRepository.findAllByDeviceIdAndExpiresAtAfterAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(
                deviceId, now, now, to
        )).thenReturn(List.of());
        var service = new ICloudCalendarService(
                connectionRepository, calendarRepository, eventRepository, deviceRepository,
                workdaySettingsService, secretCipher, calDavClient, Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(service.cachedEvents(deviceId, now, to)).isEmpty();

        verify(eventRepository)
                .findAllByDeviceIdAndExpiresAtAfterAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(
                        deviceId, now, now, to
                );
    }
}
