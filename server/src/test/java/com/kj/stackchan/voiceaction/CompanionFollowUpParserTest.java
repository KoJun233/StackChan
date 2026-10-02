package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CompanionFollowUpParserTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void explicitTopicAndLocalTimeArePinnedWithoutModelInference() {
        var draft = CompanionFollowUpParser.parse("明天下午三点问问我面试结果", "Asia/Shanghai", clock);
        assertThat(draft.scheduledAt()).isEqualTo(Instant.parse("2026-10-02T07:00:00Z"));
        assertThat(draft.content()).isEqualTo("面试结果");
        assertThat(draft.title()).contains("2026-10-02 15:00", "Asia/Shanghai");
        assertThat(draft.confirmationRequired()).isTrue();
        assertThat(CompanionFollowUpParser.parse("请十分钟后问问我工作进展", "Asia/Shanghai", clock).scheduledAt())
                .isEqualTo(clock.instant().plusSeconds(600));
    }

    @Test
    void ambiguousPastRecurringAndMissingTopicNeverCreateADraft() {
        for (String text : new String[]{"明天问我一下", "明天三点问问我面试结果", "今天上午八点问我面试结果",
                "每天15点问我面试结果", "31天后问我面试结果", "明天下午十三点问我面试结果",
                "明天下午三点问我结果", "不要明天下午三点问我面试结果"}) {
            assertThat(CompanionFollowUpParser.parse(text, "Asia/Shanghai", clock)).as(text).isNull();
        }
        assertThat(CompanionFollowUpParser.isRequest("你为什么问我这个问题")).isFalse();
        assertThat(CompanionFollowUpParser.isRequest("明天问我一下")).isTrue();
    }

    @Test
    void politePrefixIsRemovedBeforeParsingTheTime() {
        for (String prefix : new String[]{"", "请", "请你", "你"}) {
            String request = prefix + "明天下午三点问问我工作进展";
            assertThat(CompanionFollowUpParser.isRequest(request)).as(request).isTrue();
            var draft = CompanionFollowUpParser.parse(request, "Asia/Shanghai", clock);
            assertThat(draft).as(request).isNotNull();
            assertThat(draft.scheduledAt()).isEqualTo(Instant.parse("2026-10-02T07:00:00Z"));
            assertThat(CompanionFollowUpParser.parse(prefix + "十分钟后问问我工作进展",
                    "Asia/Shanghai", clock).scheduledAt()).isEqualTo(clock.instant().plusSeconds(600));
        }
    }

    @Test
    void noonIsNotShiftedToNightAndAmbiguousPeriodsRequireClarification() {
        String[] requests = {"今天中午十一点问我工作进展", "今天中午十二点问我工作进展",
                "今天中午一点问我工作进展"};
        Instant[] expected = {Instant.parse("2026-10-01T03:00:00Z"),
                Instant.parse("2026-10-01T04:00:00Z"), Instant.parse("2026-10-01T05:00:00Z")};
        for (int index = 0; index < requests.length; index++) {
            assertThat(CompanionFollowUpParser.parse(requests[index], "Asia/Shanghai", clock).scheduledAt())
                    .as(requests[index]).isEqualTo(expected[index]);
        }
        for (String request : new String[]{"明天晚上十二点问我工作进展", "明天上午十二点问我工作进展",
                "明天早上十二点问我工作进展", "明天晚上三点问我工作进展", "明天中午九点问我工作进展"}) {
            assertThat(CompanionFollowUpParser.parse(request, "Asia/Shanghai", clock)).as(request).isNull();
        }
        assertThat(CompanionFollowUpParser.parse("明天晚上十一点问我工作进展", "Asia/Shanghai", clock).scheduledAt())
                .isEqualTo(Instant.parse("2026-10-02T15:00:00Z"));
    }

    @Test
    void tomorrowUsesTheDeviceDateAfterLocalMidnightNotTheUtcDate() {
        Clock afterLocalMidnight = Clock.fixed(Instant.parse("2026-10-01T16:10:00Z"), ZoneOffset.UTC);
        var draft = CompanionFollowUpParser.parse("请你明天下午三点问我工作进展",
                "Asia/Shanghai", afterLocalMidnight);
        assertThat(draft.scheduledAt()).isEqualTo(Instant.parse("2026-10-03T07:00:00Z"));
        assertThat(draft.title()).contains("2026-10-03 15:00", "Asia/Shanghai");
        assertThat(draft.confirmationRequired()).isTrue();
        assertThat(draft.recurrenceType()).isEqualTo("NONE");
        assertThat(draft.recurrenceInterval()).isEqualTo(1);
    }
}
