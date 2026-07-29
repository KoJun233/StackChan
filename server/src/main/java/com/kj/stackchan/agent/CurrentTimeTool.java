package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;

public class CurrentTimeTool {

    public static final String ID = "current_date_time";

    private final Clock clock;
    private final ZoneId zoneId;
    private final ObjectMapper objectMapper;

    public CurrentTimeTool(Clock clock, ZoneId zoneId, ObjectMapper objectMapper) {
        this.clock = clock;
        this.zoneId = zoneId;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = ID,
            description = "返回当前用户时区中的日期、时间、星期和 IANA 时区；不接受模型指定的时区。"
    )
    public String currentDateTime() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
        return json(new CurrentTimeResult(
                now.toLocalDate().toString(),
                now.toLocalTime().withNano(0).toString(),
                now.getDayOfWeek().name(),
                zoneId.getId(),
                now.getOffset().toString()
        ));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize current time", exception);
        }
    }

    private record CurrentTimeResult(String date, String time, String dayOfWeek, String zoneId, String utcOffset) {
    }
}
