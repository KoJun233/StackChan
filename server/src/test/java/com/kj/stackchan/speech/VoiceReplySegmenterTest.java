package com.kj.stackchan.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceReplySegmenterTest {

    private final VoiceReplySegmenter segmenter = new VoiceReplySegmenter();

    @Test
    void splitsAtNaturalBoundariesInOriginalOrder() {
        assertThat(segmenter.segment("第一句。第二句！最后一句？"))
                .containsExactly("第一句。", "第二句！", "最后一句？");
    }

    @Test
    void appliesHardBoundsToUnpunctuatedReplies() {
        var segments = segmenter.segment("好".repeat(400));

        assertThat(segments)
                .hasSize(4)
                .allSatisfy(segment -> assertThat(segment.length())
                        .isLessThanOrEqualTo(VoiceReplySegmenter.HARD_SPLIT_CHARACTERS));
        assertThat(segments.getFirst())
                .hasSize(VoiceReplySegmenter.FIRST_SEGMENT_HARD_SPLIT_CHARACTERS);
    }

    @Test
    void emitsAShortFirstClauseForConversationalReplies() {
        assertThat(segmenter.segment("爸爸，小峰听得清清楚楚呢，您说吧，我一直都在～"))
                .containsExactly("爸爸，小峰听得清清楚楚呢，", "您说吧，我一直都在～");

        assertThat(segmenter.segment(
                "爸爸，今天是星期一呢，9月7号，晚上八点半啦～新的一周刚开始，您今天过得还好吗？"
        )).startsWith("爸爸，今天是星期一呢，");
    }

    @Test
    void keepsAtMostEightSegmentsAndPreservesTheWholeReply() {
        String reply = "一句。".repeat(12);
        var segments = segmenter.segment(reply);
        assertThat(segments).hasSize(VoiceReplySegmenter.MAX_SEGMENTS);
        assertThat(String.join("", segments)).isEqualTo(reply);
    }
}
