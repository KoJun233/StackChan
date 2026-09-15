package com.kj.stackchan.api;

import java.util.UUID;
import com.kj.stackchan.interaction.ProactivePauseService;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProactivePauseControllerTest {
    @Test
    void validatesDurationAndKeepsAllActionsInTheExplicitScope() throws Exception {
        var service = mock(ProactivePauseService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ProactivePauseController(service)).build();
        var device = UUID.randomUUID();
        var role = UUID.randomUUID();
        var path = "/api/v1/settings/interactions/" + device + "/roles/" + role + "/proactive-pause";
        mvc.perform(get(path)).andExpect(status().isOk());
        verify(service).get(device, role);
        mvc.perform(put(path).contentType("application/json").content("{\"minutes\":null}")).andExpect(status().isOk());
        verify(service).pause(device, role, null);
        mvc.perform(put(path).contentType("application/json").content("{\"minutes\":60}")).andExpect(status().isOk());
        verify(service).pause(device, role, 60);
        mvc.perform(put(path).contentType("application/json").content("{\"minutes\":1441}")).andExpect(status().isBadRequest());
        verify(service, never()).pause(device, role, 1441);
        mvc.perform(delete(path)).andExpect(status().isOk());
        verify(service).resume(device, role);
    }
}
