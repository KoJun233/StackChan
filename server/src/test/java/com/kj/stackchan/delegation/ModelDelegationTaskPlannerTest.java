package com.kj.stackchan.delegation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ModelDelegationTaskPlannerTest {
    private static final String DAILY="""
            {"title":"每日回顾","understanding":"每天18点汇总当前待办，结果保存在控制台。",
            "scheduleType":"DAILY","daysOfWeek":[],"localTime":"18:00","zoneId":"Asia/Shanghai",
            "onceAt":null,"intervalMinutes":null,"deliveryMode":"CONSOLE","requiresToolEvidence":true,
            "requiredToolNames":["current_personal_tasks"],"clarification":null}
            """;
    private DelegationTaskPlanner.PlanningRequest request() {
        return new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "每天18点帮我回顾待办","Asia/Shanghai",Instant.parse("2026-10-10T01:00:00Z"),null);
    }
    private ModelDelegationTaskPlanner planner(String result) {
        return new ModelDelegationTaskPlanner(new ObjectMapper(),(system,user)->result,
                ignored->List.of(Map.of("name","current_personal_tasks","description","Read current tasks")));
    }
    @Test void interpretsDescriptionWithoutResearchFieldsAndSnapshotsActualTools() {
        var result=planner(DAILY).plan(request());
        assertThat(result.localTime().toString()).isEqualTo("18:00");
        assertThat(result.allowedToolNames()).containsExactly("current_personal_tasks");
        assertThat(result.requiredToolNames()).containsExactly("current_personal_tasks");
        assertThat(result.requiresToolEvidence()).isTrue();
    }
    @Test void returnsQuestionWhenTimeIsMissing() {
        var result=planner("{\"clarification\":\"你希望每天几点进行回顾？\"}").plan(request());
        assertThat(result.clarification()).contains("几点");
        assertThat(result.scheduleType()).isNull();
    }
    @Test void cannotInventAnAvailableTool() {
        assertThatThrownBy(()->planner(DAILY.replace("[\"current_personal_tasks\"]","[\"turn_on_computer\"]"))
                .plan(request())).isInstanceOf(DelegationException.class);
    }
    @Test void duplicateOrTrailingModelJsonIsRejected() {
        assertThatThrownBy(()->planner(DAILY+DAILY).plan(request())).isInstanceOf(DelegationException.class);
        assertThatThrownBy(()->planner(DAILY.replace("\"requiresToolEvidence\":true",
                "\"requiresToolEvidence\":true,\"requiresToolEvidence\":false")).plan(request()))
                .isInstanceOf(DelegationException.class);
    }
    @Test void deliberateContentGenerationCanHaveNoExternalTools() {
        var text=DAILY.replace("\"requiresToolEvidence\":true","\"requiresToolEvidence\":false")
                .replace("[\"current_personal_tasks\"]","[]");
        var result=planner(text).plan(request());
        assertThat(result.requiresToolEvidence()).isFalse();
        assertThat(result.requiredToolNames()).isEmpty();
    }
    @Test void consoleTasksNeverSnapshotPhoneWritePermissionAndBarkTasksCan() {
        var planner=new ModelDelegationTaskPlanner(new ObjectMapper(),(system,user)->DAILY,
                ignored->List.of(Map.of("name","current_personal_tasks","description","Read tasks"),
                        Map.of("name","send_phone_notification","description","Push")));
        assertThat(planner.plan(request()).allowedToolNames()).doesNotContain("send_phone_notification");
        var phone=new ModelDelegationTaskPlanner(new ObjectMapper(),(system,user)->DAILY.replace("CONSOLE","BARK"),
                ignored->List.of(Map.of("name","current_personal_tasks","description","Read tasks"),
                        Map.of("name","send_phone_notification","description","Push")));
        assertThat(phone.plan(request()).allowedToolNames()).contains("send_phone_notification");
    }
}
