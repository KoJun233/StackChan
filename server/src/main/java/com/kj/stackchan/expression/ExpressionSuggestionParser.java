package com.kj.stackchan.expression;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExpressionSuggestionParser {
    private static final Pattern MARKER = Pattern.compile(
            "(?s)\\s*\\[\\[emotion:(NEUTRAL|HAPPY|LOVING|SAD|ANGRY|SURPRISED|CONFUSED|SHY|TIRED|FOCUSED|NERVOUS|CONTENT):(WEAK|MEDIUM|STRONG):(5|6|7|8|9|10|11|12|13|14|15)]]\\s*$"
    );
    private static final Pattern TRAILING_MARKER = Pattern.compile(
            "(?s)\\s*\\[\\[emotion:[^]\\r\\n]{1,80}]]\\s*$"
    );

    private ExpressionSuggestionParser() { }

    public static Suggestion parse(String modelReply) {
        String reply = modelReply == null ? "" : modelReply.trim();
        Matcher matcher = MARKER.matcher(reply);
        if (!matcher.find()) {
            String safeReply = TRAILING_MARKER.matcher(reply).replaceFirst("").stripTrailing();
            return new Suggestion(safeReply, CompanionEmotion.NEUTRAL, EmotionIntensity.MEDIUM, 5);
        }
        String cleanReply = reply.substring(0, matcher.start()).stripTrailing();
        return new Suggestion(
                cleanReply,
                CompanionEmotion.valueOf(matcher.group(1)),
                EmotionIntensity.valueOf(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        );
    }

    public record Suggestion(String reply, CompanionEmotion emotion,
                             EmotionIntensity intensity, int durationSeconds) { }
}
