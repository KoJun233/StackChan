package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCredentialService;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.device.InvalidDeviceRefreshCredentialException;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceCredentialController.class)
@Import(SecurityConfiguration.class)
class DeviceCredentialControllerTest {

    private static final UUID DEVICE_ID = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceCredentialService credentialService;

    @MockitoBean
    private DeviceTokenService tokenService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void refreshesAValidDeviceCredentialWithOnlyAccessTokenMetadata() throws Exception {
        DeviceEntity device = mock(DeviceEntity.class);
        when(credentialService.authenticateRefresh(DEVICE_ID, "device-refresh-token")).thenReturn(device);
        when(tokenService.issue(device)).thenReturn(new DeviceTokenService.IssuedDeviceToken(
                "device-access-token",
                Instant.parse("2026-01-02T00:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/devices/token:refresh")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"a88e4a94-8536-4fa1-91ed-8681b597429d","refreshToken":"device-refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "accessToken":"device-access-token",
                          "accessTokenExpiresAt":"2026-01-02T00:00:00Z",
                          "wsUrl":"/api/v1/ws/device"
                        }
                        """, STRICT));
    }

    @Test
    void rejectsUnknownDevicesAndWrongRefreshTokensWithTheSameGenericUnauthorizedResponse() throws Exception {
        UUID unknownDeviceId = UUID.fromString("82d51c37-fb26-4731-b7ca-195649453fb1");
        when(credentialService.authenticateRefresh(DEVICE_ID, "wrong-refresh-token"))
                .thenThrow(new InvalidDeviceRefreshCredentialException());
        when(credentialService.authenticateRefresh(unknownDeviceId, "device-refresh-token"))
                .thenThrow(new InvalidDeviceRefreshCredentialException());

        mockMvc.perform(post("/api/v1/devices/token:refresh")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"a88e4a94-8536-4fa1-91ed-8681b597429d","refreshToken":"wrong-refresh-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"device_credentials_invalid","message":"设备凭据无效，请通过 USB 重新配对。"}
                        """, STRICT));

        mockMvc.perform(post("/api/v1/devices/token:refresh")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"82d51c37-fb26-4731-b7ca-195649453fb1","refreshToken":"device-refresh-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"device_credentials_invalid","message":"设备凭据无效，请通过 USB 重新配对。"}
                        """, STRICT));
    }

    @Test
    void rejectsStoredMissingAndReplacedCredentialsWithTheSameGenericUnauthorizedResponse() throws Exception {
        when(credentialService.authenticateRefresh(DEVICE_ID, "presented-credential"))
                .thenThrow(new InvalidDeviceRefreshCredentialException());
        when(credentialService.authenticateRefresh(DEVICE_ID, "retired-credential"))
                .thenThrow(new InvalidDeviceRefreshCredentialException());

        assertInvalidRefresh("""
                {"deviceId":"a88e4a94-8536-4fa1-91ed-8681b597429d","refreshToken":"presented-credential"}
                """);
        assertInvalidRefresh("""
                {"deviceId":"a88e4a94-8536-4fa1-91ed-8681b597429d","refreshToken":"retired-credential"}
                """);
    }

    @Test
    void rejectsMissingOrBlankRefreshFieldsWithTheGenericUnauthorizedResponse() throws Exception {
        assertInvalidRefresh("""
                {"refreshToken":"presented-credential"}
                """);
        assertInvalidRefresh("""
                {"deviceId":"a88e4a94-8536-4fa1-91ed-8681b597429d"}
                """);
        assertInvalidRefresh("""
                {"deviceId":"a88e4a94-8536-4fa1-91ed-8681b597429d","refreshToken":"   "}
                """);
    }

    private void assertInvalidRefresh(String requestBody) throws Exception {
        mockMvc.perform(post("/api/v1/devices/token:refresh")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"device_credentials_invalid","message":"设备凭据无效，请通过 USB 重新配对。"}
                        """, STRICT));
    }
}
