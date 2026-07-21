package com.kj.stackchan.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@Import(SecurityConfiguration.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceRepository deviceRepository;

    @MockitoBean
    private DeviceCommandGateway deviceCommandGateway;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void listsOnlyPublicDeviceFieldsSortedByDisplayNameThenId() throws Exception {
        DeviceEntity zulu = device(
                "00000000-0000-0000-0000-000000000002",
                "Zulu",
                "2.0.0",
                "motion_enabled",
                null
        );
        DeviceEntity alphaLater = device(
                "00000000-0000-0000-0000-000000000003",
                "Alpha",
                "1.0.1",
                "motion_disabled",
                Instant.parse("2026-07-17T14:58:29Z")
        );
        DeviceEntity alphaFirst = device(
                "00000000-0000-0000-0000-000000000001",
                "Alpha",
                "1.0.0",
                "motion_disabled",
                Instant.parse("2026-07-17T14:59:30Z")
        );
        when(clock.instant()).thenReturn(Instant.parse("2026-07-17T15:00:00Z"));
        when(deviceRepository.findAll()).thenReturn(List.of(zulu, alphaLater, alphaFirst));

        mockMvc.perform(get("/api/v1/devices").with(user("admin").roles("ADMIN")).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"devices":[
                          {"id":"00000000-0000-0000-0000-000000000001","displayName":"Alpha","firmwareVersion":"1.0.0","safetyState":"motion_disabled","lastSeenAt":"2026-07-17T14:59:30Z","online":true,"commandAvailable":false},
                          {"id":"00000000-0000-0000-0000-000000000003","displayName":"Alpha","firmwareVersion":"1.0.1","safetyState":"motion_disabled","lastSeenAt":"2026-07-17T14:58:29Z","online":false,"commandAvailable":false},
                          {"id":"00000000-0000-0000-0000-000000000002","displayName":"Zulu","firmwareVersion":"2.0.0","safetyState":"motion_enabled","lastSeenAt":null,"online":false,"commandAvailable":false}
                        ]}
                        """, STRICT));
    }

    @Test
    void rejectsStopMotionWhenTheDeviceIsOffline() throws Exception {
        UUID deviceId = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");
        when(deviceCommandGateway.stopMotion(deviceId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/devices/{deviceId}/commands/stop-motion", deviceId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {"code":"device_offline","message":"设备当前离线，无法接收安全停止命令。"}
                        """, STRICT));
    }

    @Test
    void acceptsStopMotionWhenTheDeviceIsConnected() throws Exception {
        UUID deviceId = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");
        when(deviceCommandGateway.stopMotion(deviceId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/devices/{deviceId}/commands/stop-motion", deviceId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isAccepted());
    }

    private DeviceEntity device(
            String id,
            String displayName,
            String firmwareVersion,
            String safetyState,
            Instant lastSeenAt
    ) {
        DeviceEntity device = mock(DeviceEntity.class);
        when(device.getId()).thenReturn(UUID.fromString(id));
        when(device.getDisplayName()).thenReturn(displayName);
        when(device.getFirmwareVersion()).thenReturn(firmwareVersion);
        when(device.getSafetyState()).thenReturn(safetyState);
        when(device.getLastSeenAt()).thenReturn(lastSeenAt);
        return device;
    }
}
