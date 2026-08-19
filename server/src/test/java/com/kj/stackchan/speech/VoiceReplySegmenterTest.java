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
        assertThat(segmenter.segment("好".repeat(400)))
                .hasSize(3)
                .allSatisfy(segment -> assertThat(segment.length())
                        .isLessThanOrEqualTo(VoiceReplySegmenter.HARD_SPLIT_CHARACTERS));
    }

    @Test
    void keepsAtMostEightSegmentsAndPreservesTheWholeReply() {
        String reply = "一句。".repeat(12);
        var segments = segmenter.segment(reply);
        assertThat(segments).hasSize(VoiceReplySegmenter.MAX_SEGMENTS);
        assertThat(String.join("", segments)).isEqualTo(reply);
    }
}
