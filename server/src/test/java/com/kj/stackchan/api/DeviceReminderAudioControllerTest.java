package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.reminder.ReminderDeliveryService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceReminderAudioController.class)
@Import({SecurityConfiguration.class, ObjectMapper.class})
class DeviceReminderAudioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceHttpAuthenticator authenticator;

    @MockitoBean
    private ReminderDeliveryService reminderDeliveryService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void returnsOnlyAudioOwnedByTheAuthenticatedDevice() throws Exception {
        UUID reminderId = UUID.randomUUID();
        UUID authenticatedDeviceId = UUID.randomUUID();
        byte[] audio = new byte[44];
        when(authenticator.authenticate(any())).thenReturn(new DeviceTokenService.DeviceToken(
                authenticatedDeviceId,
                3,
                Instant.parse("2026-07-20T00:00:00Z")
        ));
        when(reminderDeliveryService.getAudio(reminderId, authenticatedDeviceId)).thenReturn(audio);

        mockMvc.perform(get("/api/v1/device/reminders/{reminderId}/audio", reminderId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(header().longValue("Content-Length", audio.length))
                .andExpect(content().bytes(audio));

        verify(reminderDeliveryService).getAudio(reminderId, authenticatedDeviceId);
    }
}
