package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.wakeword.EspSrWakeWordModelCatalog;
import com.kj.stackchan.wakeword.WakeWordModelJobEntity;
import com.kj.stackchan.wakeword.WakeWordModelJobService;
import com.kj.stackchan.wakeword.WakeWordModelJobStatus;
import com.kj.stackchan.wakeword.WakeWordModelOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WakeWordModelController.class)
@Import(SecurityConfiguration.class)
class WakeWordModelControllerTest {

    private static final UUID DEVICE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String MODEL_NAME = "wn9_xiao3feng1xiao3feng1_tts3";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WakeWordModelJobService jobService;

    @MockitoBean
    private EspSrWakeWordModelCatalog catalog;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void listsOnlyTrustedBuiltInModelsForAnAdministrator() throws Exception {
        when(catalog.options()).thenReturn(List.of(
                new WakeWordModelOption(MODEL_NAME, "小峰小峰", "zh-CN")
        ));

        mockMvc.perform(get("/api/v1/wake-word-model-jobs/catalog")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].modelName").value(MODEL_NAME))
                .andExpect(jsonPath("$.models[0].phrase").value("小峰小峰"));
    }

    @Test
    void createsAReadyBuiltInModelJobForAnAdministrator() throws Exception {
        UUID jobId = UUID.fromString("111e8400-e29b-41d4-a716-446655440000");
        WakeWordModelJobEntity job = mock(WakeWordModelJobEntity.class);
        when(job.getId()).thenReturn(jobId);
        when(job.getDeviceId()).thenReturn(DEVICE_ID);
        when(job.getPhrase()).thenReturn("小峰小峰");
        when(job.getStatus()).thenReturn(WakeWordModelJobStatus.READY);
        when(job.getModelName()).thenReturn(MODEL_NAME);
        when(job.getCreatedAt()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        when(job.getUpdatedAt()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        when(jobService.create(DEVICE_ID, MODEL_NAME)).thenReturn(job);

        mockMvc.perform(post("/api/v1/wake-word-model-jobs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"550e8400-e29b-41d4-a716-446655440000","modelName":"wn9_xiao3feng1xiao3feng1_tts3"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.modelName").value(MODEL_NAME))
                .andExpect(jsonPath("$.phrase").value("小峰小峰"))
                .andExpect(jsonPath("$.source").doesNotExist());

        verify(jobService).create(DEVICE_ID, MODEL_NAME);
    }

    @Test
    void rejectsTheRetiredFreeTextRequestShape() throws Exception {
        mockMvc.perform(post("/api/v1/wake-word-model-jobs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"550e8400-e29b-41d4-a716-446655440000","phrase":"任意短语"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noLongerExposesTheModelUploadEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/wake-word-model-jobs/upload")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
