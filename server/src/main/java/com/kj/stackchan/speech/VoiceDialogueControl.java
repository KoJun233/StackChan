package com.kj.stackchan.speech;

import java.util.regex.Pattern;

/** Explicit user controls only; never infer a command from quoted or negated text. */
public record VoiceDialogueControl(Kind kind, String routingText) {
    public enum Kind { NONE, NEW_TOPIC, CORRECTION, CONTINUE, END, DECLINE }
    private static final Pattern END = Pattern.compile("^结束聊天[\\s。！？，、；．.,!?;]*$");
    private static final Pattern DECLINE = Pattern.compile("^(?:不聊这个(?:了)?|先不聊(?:了)?|现在不聊(?:了)?|现在忙|我现在很忙)[。！!，,]?$");

    public boolean closesTopic() { return kind == Kind.END || kind == Kind.DECLINE; }

    private static final Pattern NEW_TOPIC = Pattern.compile(
            "^(?:我们|咱们)?(?:换个话题|换一个话题|聊点别的)(?:[。！!？?]?$|[，,:：]\\s*.+$)");
    private static final Pattern CORRECTION = Pattern.compile(
            "^(?:不对[，,]\\s*)?不是.{1,80}?[，,]\\s*(?:而是|是|我问的是|我说的是)(.+)$");
    private static final Pattern CONTINUE = Pattern.compile(
            "^(?:继续|继续讲|接着讲|接着说|继续说|详细说说|展开说说|再详细一点)[。！!？?]?$"
    );

    public static VoiceDialogueControl parse(String input) {
        String endText = input == null ? "" : input.trim();
        if (END.matcher(endText).matches()) return new VoiceDialogueControl(Kind.END, endText);
        String text = input == null ? "" : input.strip();
        if (DECLINE.matcher(text).matches()) return new VoiceDialogueControl(Kind.DECLINE, text);
        if (NEW_TOPIC.matcher(text).matches()) return new VoiceDialogueControl(Kind.NEW_TOPIC, text);
        var correction = CORRECTION.matcher(text);
        if (correction.matches()) return new VoiceDialogueControl(Kind.CORRECTION, correction.group(1).strip());
        if (text.startsWith("我问的是") && text.length() > 4) {
            return new VoiceDialogueControl(Kind.CORRECTION, text.substring(4).strip());
        }
        if (CONTINUE.matcher(text).matches()) return new VoiceDialogueControl(Kind.CONTINUE, text);
        return new VoiceDialogueControl(Kind.NONE, text);
    }

    public String guidance() {
        return switch (kind) {
            case NEW_TOPIC -> "\n用户明确要求换话题。不要续接旧话题或旧主动播报；有新对象就直接回应，没有则简短询问想聊什么。";
            case CORRECTION -> "\n用户正在纠正本轮理解。以这次明确的新对象为准，简短承认后回答，不为旧解释辩解；不把本轮纠正自动当作长期偏好变化。";
            case CONTINUE -> "\n用户要求续讲。沿最近实际回答补充下一点，不重新复述开头；没有近期对象则简短澄清。旧工具结果不能冒充新的动态事实查询，不能补造未读取的文章内容。";
            case NONE -> "";
            case END, DECLINE -> "";
        };
    }
}
