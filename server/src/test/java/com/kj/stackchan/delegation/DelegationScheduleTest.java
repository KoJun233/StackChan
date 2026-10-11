package com.kj.stackchan.delegation;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.kj.stackchan.delegation.DelegationService.*;

class DelegationScheduleTest {
    @Test void calculatesShanghaiDailyAndWeeklyFromTheLocalDate() {
        var daily=input(ScheduleType.DAILY,List.of(),"Asia/Shanghai",LocalTime.of(8,30));
        assertThat(DelegationSchedule.next(daily,Instant.parse("2026-10-08T23:00:00Z"))).isEqualTo(Instant.parse("2026-10-09T00:30:00Z"));
        assertThat(DelegationSchedule.next(daily,Instant.parse("2026-10-09T00:30:00Z"))).isEqualTo(Instant.parse("2026-10-10T00:30:00Z"));
        var weekly=input(ScheduleType.WEEKLY,List.of(1,5),"Asia/Shanghai",LocalTime.of(8,30));
        assertThat(DelegationSchedule.next(weekly,Instant.parse("2026-10-09T01:00:00Z"))).isEqualTo(Instant.parse("2026-10-12T00:30:00Z"));
    }
    @Test void handlesDstGapAndOverlapWithOneLocalOccurrence() {
        var gap=input(ScheduleType.DAILY,List.of(),"America/New_York",LocalTime.of(2,30));
        assertThat(DelegationSchedule.next(gap,Instant.parse("2026-03-08T05:00:00Z"))).isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
        var overlap=input(ScheduleType.DAILY,List.of(),"America/New_York",LocalTime.of(1,30));
        assertThat(DelegationSchedule.next(overlap,Instant.parse("2026-11-01T05:31:00Z"))).isEqualTo(Instant.parse("2026-11-02T06:30:00Z"));
    }
    @Test void intervalsUseAnAnchorRatherThanOneLocalDayAndOneTimeSchedulesTerminate() {
        Instant anchor=Instant.parse("2026-10-09T00:00:00Z");
        var interval=new TaskInput(UUID.randomUUID(),UUID.randomUUID(),"任务","描述",List.of(),ScheduleType.INTERVAL,List.of(),null,
                "Asia/Shanghai",5,DeliveryMode.CONSOLE,true,"描述",anchor,15);
        assertThat(DelegationSchedule.next(interval,anchor.plusSeconds(1000))).isEqualTo(anchor.plusSeconds(1800));
        assertThat(DelegationSchedule.occurrence(interval,anchor.plusSeconds(1000))).isEqualTo(anchor.plusSeconds(900));
        var once=new TaskInput(UUID.randomUUID(),UUID.randomUUID(),"任务","描述",List.of(),ScheduleType.ONCE,List.of(),null,
                "Asia/Shanghai",5,DeliveryMode.CONSOLE,true,"描述",anchor,null);
        assertThat(DelegationSchedule.next(once,anchor.minusSeconds(1))).isEqualTo(anchor);
        assertThat(DelegationSchedule.next(once,anchor)).isNull();
    }
    private TaskInput input(ScheduleType type,List<Integer> days,String zone,LocalTime time) {
        return new TaskInput(UUID.randomUUID(),UUID.randomUUID(),"标题","要求",List.of("https://example.com/rss"),type,days,time,zone,5,DeliveryMode.CONSOLE,true);
    }
}
