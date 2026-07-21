package com.kj.stackchan.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.device.PairingCodeEntity;
import com.kj.stackchan.device.PairingService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PairingController.class)
@Import(SecurityConfiguration.class)
class PairingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PairingService pairingService;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void createsAPairingCodeForAValidCreator() throws Exception {
        when(pairingService.createCode("operator"))
                .thenReturn(new PairingCodeEntity(
                        "pairing-code",
                        "operator",
                        Instant.parse("2026-01-01T00:10:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/pairing/codes")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"createdBy\":\"operator\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"value":"pairing-code","expiresAt":"2026-01-01T00:10:00Z"}
                        """, STRICT));
    }

    @Test
    void rejectsInvalidPairingRequests() throws Exception {
        mockMvc.perform(post("/api/v1/pairing/codes")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"createdBy\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_request","message":"请求参数无效。"}
                        """, STRICT));

        mockMvc.perform(post("/api/v1/pairing/claim")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pairingCode\":\"\",\"hardwareId\":\"hardware\",\"firmwareVersion\":\"1.2.3\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {"code":"pairing_code_unavailable","message":"配对码无效、已使用或已过期。"}
                        """, STRICT));
    }

    @Test
    void rejectsMalformedPairingJsonWithTheGenericSafeResponse() throws Exception {
        mockMvc.perform(post("/api/v1/pairing/codes")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"createdBy\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_request","message":"请求参数无效。"}
                        """, STRICT));
    }

    @Test
    void rejectsPairingFieldsThatExceedTheirStorageLimits() throws Exception {
        mockMvc.perform(post("/api/v1/pairing/codes")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"createdBy\":\"%s\"}".formatted("a".repeat(81))))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_request","message":"请求参数无效。"}
                        """, STRICT));

        mockMvc.perform(post("/api/v1/pairing/claim")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pairingCode\":\"%s\",\"hardwareId\":\"hardware\",\"firmwareVersion\":\"1.2.3\"}"
                                .formatted("a".repeat(13))))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_request","message":"请求参数无效。"}
                        """, STRICT));

        mockMvc.perform(post("/api/v1/pairing/claim")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pairingCode\":\"pairing-code\",\"hardwareId\":\"%s\",\"firmwareVersion\":\"1.2.3\"}"
                                .formatted("a".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_request","message":"请求参数无效。"}
                        """, STRICT));

        mockMvc.perform(post("/api/v1/pairing/claim")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pairingCode\":\"pairing-code\",\"hardwareId\":\"hardware\",\"firmwareVersion\":\"%s\"}"
                                .formatted("a".repeat(33))))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_request","message":"请求参数无效。"}
                        """, STRICT));
    }

    @Test
    void claimsDeviceAndReturnsItsScopedAccessTokenContract() throws Exception {
        UUID deviceId = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");
        DeviceEntity device = mock(DeviceEntity.class);
        when(device.getId()).thenReturn(deviceId);
        when(pairingService.claim("pairing-code", "stackchan-001", "1.2.3"))
                .thenReturn(Optional.of(new PairingService.PairingClaim(device, "device-refresh-token", 0)));
        when(deviceTokenService.issue(device)).thenReturn(new DeviceTokenService.IssuedDeviceToken(
                "device-access-token",
                Instant.parse("2026-01-02T00:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/pairing/claim")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"pairingCode":"pairing-code","hardwareId":"stackchan-001","firmwareVersion":"1.2.3"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {
                          "deviceId":"a88e4a94-8536-4fa1-91ed-8681b597429d",
                          "accessToken":"device-access-token",
                          "accessTokenExpiresAt":"2026-01-02T00:00:00Z",
                          "refreshToken":"device-refresh-token",
                          "refreshUrl":"/api/v1/devices/token:refresh",
                          "wsUrl":"/api/v1/ws/device"
                        }
                        """, STRICT));
    }

    @Test
    void returnsConflictForAnUnavailablePairingCode() throws Exception {
        when(pairingService.claim("expired-code", "stackchan-001", "1.2.3"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/pairing/claim")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"pairingCode":"expired-code","hardwareId":"stackchan-001","firmwareVersion":"1.2.3"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {"code":"pairing_code_unavailable","message":"配对码无效、已使用或已过期。"}
                        """, STRICT));
    }
}
