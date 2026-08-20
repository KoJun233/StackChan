package com.kj.stackchan.expression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExpressionSuggestionParserTest {
    @Test
    void extractsOnlyTheBoundedTrailingMarker() {
        var suggestion = ExpressionSuggestionParser.parse(
                "今天见到你真开心。\n[[emotion:HAPPY:STRONG:12]]");
        assertThat(suggestion.reply()).isEqualTo("今天见到你真开心。");
        assertThat(suggestion.emotion()).isEqualTo(CompanionEmotion.HAPPY);
        assertThat(suggestion.intensity()).isEqualTo(EmotionIntensity.STRONG);
        assertThat(suggestion.durationSeconds()).isEqualTo(12);
    }

    @Test
    void invalidMarkerIsNeverSpokenAndFallsBackToNeutral() {
        var suggestion = ExpressionSuggestionParser.parse("普通回答 [[emotion:ADMIN:MAX:99]]");
        assertThat(suggestion.reply()).isEqualTo("普通回答");
        assertThat(suggestion.emotion()).isEqualTo(CompanionEmotion.NEUTRAL);
        assertThat(suggestion.durationSeconds()).isEqualTo(5);
    }

    @Test
    void replyWithoutMarkerIsUnchanged() {
        assertThat(ExpressionSuggestionParser.parse("普通回答").reply()).isEqualTo("普通回答");
    }

    @Test
    void markerOnlyReplyDoesNotLeakControlText() {
        var suggestion = ExpressionSuggestionParser.parse("[[emotion:HAPPY:MEDIUM:5]]");

        assertThat(suggestion.reply()).isEmpty();
        assertThat(suggestion.emotion()).isEqualTo(CompanionEmotion.HAPPY);
    }
}
