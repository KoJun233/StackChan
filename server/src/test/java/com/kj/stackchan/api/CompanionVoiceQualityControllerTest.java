package com.kj.stackchan.api;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.speech.VoiceQualityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanionVoiceQualityController.class)
@Import(SecurityConfiguration.class)
class CompanionVoiceQualityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VoiceQualityService quality;
    @MockitoBean AdminUserRepository admins;

    @Test void unauthenticatedCannotReadAnyDeviceQuality() throws Exception {
        mvc.perform(get("/api/v1/companion/voice-quality").param("deviceId",UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(quality);
    }
    @Test @WithMockUser(roles="USER") void authenticatedNonOwnerCannotReadAnyQuality() throws Exception {
        mvc.perform(get("/api/v1/companion/voice-quality").param("deviceId",UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(quality);
    }
    @Test @WithMockUser(roles="ADMIN") void malformedOrMissingInputHasFixedErrorWithoutEcho() throws Exception {
        for (String query : new String[]{"", "?deviceId=private-invalid", "?deviceId="+UUID.randomUUID()+"&from=private-time"}) {
            mvc.perform(get("/api/v1/companion/voice-quality"+query)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(quality);
    }
    @Test @WithMockUser(roles="ADMIN") void inaccessibleDeviceReturnsGenericNotFound() throws Exception {
        UUID id=UUID.randomUUID();
        when(quality.summary(id,null,null)).thenThrow(new VoiceQualityService.QualityRequestException(true));
        mvc.perform(get("/api/v1/companion/voice-quality").param("deviceId",id.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
    @Test @WithMockUser(roles="ADMIN") void returnsOnlyAnonymousCountsAndMeasurementsAndDisablesCaching() throws Exception {
        UUID id=UUID.randomUUID();
        var metric=new VoiceQualityService.Metric("DEVICE_MONOTONIC",0,0,30,null,null,null,0);
        when(quality.summary(id,null,null)).thenReturn(new VoiceQualityService.Summary(id,
                Instant.parse("2026-10-12T00:00:00Z"),Instant.parse("2026-10-13T00:00:00Z"),0,0,0,0,0,
                Map.of(),metric,metric,true,0,0));
        mvc.perform(get("/api/v1/companion/voice-quality").param("deviceId",id.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.totalAttempts").value(0)).andExpect(jsonPath("$.endpointToFirstSound.p50Ms").isEmpty())
                .andExpect(jsonPath("$.transcript").doesNotExist()).andExpect(jsonPath("$.reply").doesNotExist())
                .andExpect(jsonPath("$.audio").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
    }
}
