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
        var phoneRequest=new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "每天18点帮我回顾待办并发手机","Asia/Shanghai",request().now(),null);
        assertThat(phone.plan(phoneRequest).allowedToolNames()).contains("send_phone_notification");
    }
    @Test void clearOneTimeWorkDefaultsToServerNowEvenWhenModelInventsADailySchedule() {
        var request=new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "现在帮我比较这两个公开页面，先做一次","Asia/Shanghai",request().now(),null);
        var result=planner(DAILY).plan(request);
        assertThat(result.scheduleType()).isEqualTo(DelegationService.ScheduleType.ONCE);
        assertThat(result.onceAt()).isEqualTo(request.now());
        assertThat(result.localTime()).isNull(); assertThat(result.daysOfWeek()).isEmpty();
        assertThat(result.intervalMinutes()).isNull();
    }
    @Test void anUnscheduledDelegationAlsoMeansOneTimeAndNeverInventsPhoneAuthorization() {
        var request=new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "帮我整理这些公开资料","Asia/Shanghai",request().now(),null);
        var result=planner(DAILY.replace("CONSOLE","BARK")).plan(request);
        assertThat(result.scheduleType()).isEqualTo(DelegationService.ScheduleType.ONCE);
        assertThat(result.onceAt()).isEqualTo(request.now());
        assertThat(result.deliveryMode()).isEqualTo(DelegationService.DeliveryMode.CONSOLE);
    }
    @Test void negatedPhoneMentionCannotGrantPhoneDelivery() {
        var request=new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "现在帮我比较公开资料，不要发手机","Asia/Shanghai",request().now(),null);
        assertThat(planner(DAILY.replace("CONSOLE","BARK")).plan(request).deliveryMode())
                .isEqualTo(DelegationService.DeliveryMode.CONSOLE);
    }
    @Test void futureOneTimeScheduleIsPreserved() {
        var request=new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "明天九点帮我整理公开资料","Asia/Shanghai",request().now(),null);
        var once=DAILY.replace("\"scheduleType\":\"DAILY\"","\"scheduleType\":\"ONCE\"")
                .replace("\"onceAt\":null","\"onceAt\":\"2026-10-11T01:00:00Z\"");
        var result=planner(once).plan(request);
        assertThat(result.scheduleType()).isEqualTo(DelegationService.ScheduleType.ONCE);
        assertThat(result.onceAt()).isEqualTo(Instant.parse("2026-10-11T01:00:00Z"));
    }
    @Test void datesInSourceUrlsDoNotBecomeASchedule() {
        var request=new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "帮我比较 https://example.com/2026-10-11/design 和 https://example.org/2026-10-12/desk",
                "Asia/Shanghai",request().now(),null);
        assertThat(planner(DAILY).plan(request).scheduleType()).isEqualTo(DelegationService.ScheduleType.ONCE);
    }
    @Test void unscheduledWorkRejectsAnInventedDailyTimeQuestion() {
        var request=new DelegationTaskPlanner.PlanningRequest(UUID.randomUUID(),UUID.randomUUID(),
                "现在帮我整理公开资料","Asia/Shanghai",request().now(),null);
        assertThatThrownBy(()->planner("{\"clarification\":\"希望每天几点整理？\"}").plan(request))
                .isInstanceOf(DelegationException.class).extracting(error->((DelegationException)error).code())
                .isEqualTo("DELEGATION_PLAN_INVALID");
    }
}
