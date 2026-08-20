package com.kj.stackchan.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kj.stackchan.expression.DeviceExpressionService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceExpressionController.class)
@Import(SecurityConfiguration.class)
class DeviceExpressionControllerTest {
    private static final UUID DEVICE_ID =
            UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DeviceExpressionService expressionService;
    @MockitoBean private AdminUserRepository adminUserRepository;

    @Test
    void readsAndUpdatesFrameRateWithAdminCsrfBoundary() throws Exception {
        DeviceExpressionService.FrameRateSettings settings =
                new DeviceExpressionService.FrameRateSettings("ADAPTIVE", 24, 57, true);
        when(expressionService.getFrameRateSettings(DEVICE_ID)).thenReturn(settings);
        when(expressionService.configureFrameRate(DEVICE_ID, "ADAPTIVE", 24, 57))
                .thenReturn(settings);

        mockMvc.perform(get("/api/v1/devices/{deviceId}/expression/frame-rate", DEVICE_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"mode\":\"ADAPTIVE\",\"minFps\":24,\"maxFps\":57,\"applied\":true}"));
        mockMvc.perform(put("/api/v1/devices/{deviceId}/expression/frame-rate", DEVICE_ID)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"mode\":\"ADAPTIVE\",\"minFps\":24,\"maxFps\":57}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/devices/{deviceId}/expression/frame-rate", DEVICE_ID)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"mode\":\"ADAPTIVE\",\"minFps\":24,\"maxFps\":57}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/devices/{deviceId}/expression/frame-rate", DEVICE_ID)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"mode\":\"FIXED\",\"minFps\":61,\"maxFps\":61}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendsOnlyAuthenticatedPreviewRequests() throws Exception {
        when(expressionService.preview(DEVICE_ID, "EMOTION", "HAPPY", 5)).thenReturn(true);

        mockMvc.perform(post("/api/v1/devices/{deviceId}/expression/preview", DEVICE_ID)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"category\":\"EMOTION\",\"value\":\"HAPPY\",\"durationSeconds\":5}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"accepted\":true}"));
        verify(expressionService).preview(DEVICE_ID, "EMOTION", "HAPPY", 5);
    }
}
