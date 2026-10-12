package com.kj.stackchan.api;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.delegation.DelegationException;
import com.kj.stackchan.delegation.DelegationService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.kj.stackchan.delegation.DelegationService.*;

@WebMvcTest({DelegationController.class,DelegationDeliveryController.class})
@Import({SecurityConfiguration.class,DelegationExceptionHandler.class})
class DelegationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DelegationService service;
    @MockitoBean AdminUserRepository administrators;
    private final UUID id=UUID.randomUUID(),device=UUID.randomUUID(),role=UUID.randomUUID();

    @Test void everyEndpointRequiresAdministratorAuthentication() throws Exception {
        var requests=List.of(get("/api/v1/delegations"),get("/api/v1/delegations/{id}",id),get("/api/v1/delegations/{id}/runs",id),
                get("/api/v1/delegation-push/settings"),post("/api/v1/delegations").contentType(APPLICATION_JSON).content("{}"),
                put("/api/v1/delegations/{id}",id).contentType(APPLICATION_JSON).content("{}"),delete("/api/v1/delegations/{id}",id),
                post("/api/v1/delegations/{id}:pause",id),post("/api/v1/delegations/{id}:resume",id),post("/api/v1/delegations/{id}:run",id),
                post("/api/v1/delegation-runs/{id}:retry-delivery",id),put("/api/v1/delegation-push/settings").contentType(APPLICATION_JSON).content("{}"));
        for(var request:requests) mvc.perform(request.with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void deviceOrIntegrationAuthorityCannotManageDelegations() throws Exception {
        mvc.perform(get("/api/v1/delegations").with(user("device").roles("DEVICE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/delegation-push/settings").with(user("integration").roles("NOTIFICATION_INTEGRATION"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void listsScopedTasksAndReadableHistory() throws Exception {
        when(service.list(device,role,0,20)).thenReturn(new TaskPage(List.of(snapshot()),1));
        when(service.runs(id,0,20)).thenReturn(new RunPage(List.of(),0));
        mvc.perform(get("/api/v1/delegations").with(user("admin").roles("ADMIN")).param("deviceId",device.toString()).param("roleId",role.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.list[0].localTime").value("08:30")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/delegations/{id}/runs",id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
    @Test void taskInputUsesExplicitOwnershipAndUpdatesCannotMoveIt() throws Exception {
        when(service.create(any())).thenReturn(snapshot());
        when(service.update(eq(id),any())).thenThrow(new DelegationException("DELEGATION_SCOPE_IMMUTABLE"));
        mvc.perform(post("/api/v1/delegations").with(user("admin").roles("ADMIN")).with(csrf()).contentType(APPLICATION_JSON).content(taskJson()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roleId").value(role.toString()));
        verify(service).create(new TaskInput(device,role,"每日资料","整理可靠来源",List.of("https://example.com/feed"),ScheduleType.DAILY,List.of(),LocalTime.of(8,30),"Asia/Shanghai",5,DeliveryMode.CONSOLE,true));
        mvc.perform(put("/api/v1/delegations/{id}",id).with(user("admin").roles("ADMIN")).with(csrf()).contentType(APPLICATION_JSON).content(taskJson()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DELEGATION_SCOPE_IMMUTABLE"));
    }
    @Test void settingsDoNotReturnTheWriteOnlyDeviceKey() throws Exception {
        var snapshot=new PushSettingsSnapshot("https://api.day.app",true,true,Instant.EPOCH);
        when(service.savePushSettings(any())).thenReturn(snapshot); when(service.pushSettings()).thenReturn(snapshot);
        mvc.perform(put("/api/v1/delegation-push/settings").with(user("admin").roles("ADMIN")).with(csrf()).contentType(APPLICATION_JSON)
                        .content("""
                                {"serverUrl":"https://api.day.app","deviceKey":"fixture-only-secret","clearDeviceKey":false,"enabled":true}
                                """))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-only-secret"))))
                .andExpect(jsonPath("$.deviceKey").doesNotExist()).andExpect(jsonPath("$.keyConfigured").value(true));
        mvc.perform(get("/api/v1/delegation-push/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deviceKey").doesNotExist());
    }
    @Test void malformedInputReturnsFixedErrorWithoutEchoingThePayload() throws Exception {
        mvc.perform(post("/api/v1/delegations").with(user("admin").roles("ADMIN")).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"scheduleType\":\"fixture-secret-invalid-enum\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DELEGATION_INPUT_INVALID"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-secret-invalid-enum"))));
    }
    @Test void naturalDescriptionNeedsNoResearchOrScheduleFieldsAndReturnsTheServersUnderstanding() throws Exception {
        when(service.create(any())).thenReturn(snapshot());
        mvc.perform(post("/api/v1/delegations").with(user("admin").roles("ADMIN")).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"deviceId\":\""+device+"\",\"roleId\":\""+role+"\",\"description\":\"每天九点给我一个写作练习\",\"enabled\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("整理可靠来源"))
                .andExpect(jsonPath("$.understanding").value("整理可靠来源"));
        verify(service).create(argThat(input->"每天九点给我一个写作练习".equals(input.description())
                && input.scheduleType()==null && input.sourceUrls()==null && input.deliveryMode()==null));
    }
    @Test void missingInformationReturnsAReadableQuestionWithoutCreatingADraft() throws Exception {
        when(service.create(any())).thenThrow(new DelegationException("DELEGATION_CLARIFICATION_REQUIRED","每天几点执行？"));
        mvc.perform(post("/api/v1/delegations").with(user("admin").roles("ADMIN")).with(csrf()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.code").value("DELEGATION_CLARIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("每天几点执行？")).andExpect(jsonPath("$.clarification").value("每天几点执行？"));
    }
    private String taskJson() {
        return """
                {"deviceId":"%s","roleId":"%s","title":"每日资料","instruction":"整理可靠来源","sourceUrls":["https://example.com/feed"],
                  "scheduleType":"DAILY","daysOfWeek":[],"localTime":"08:30","zoneId":"Asia/Shanghai","maxItems":5,"deliveryMode":"CONSOLE","enabled":true}
                """.formatted(device,role);
    }
    private TaskSnapshot snapshot() {
        return new TaskSnapshot(id,device,role,"每日资料","整理可靠来源",List.of("https://example.com/feed"),ScheduleType.DAILY,List.of(),LocalTime.of(8,30),
                "Asia/Shanghai",5,DeliveryMode.CONSOLE,true,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,null);
    }
}
