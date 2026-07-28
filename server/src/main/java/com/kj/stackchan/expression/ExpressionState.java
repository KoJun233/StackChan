package com.kj.stackchan.expression;

import java.util.Arrays;

public enum ExpressionState {
    IDLE("idle"),
    LISTENING("listening"),
    PROCESSING("processing"),
    SPEAKING("speaking"),
    SUCCESS("success"),
    NO_SPEECH("no_speech"),
    OFFLINE("offline"),
    ERROR("error");

    private final String wireName;

    ExpressionState(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static ExpressionState fromWireName(String value) {
        return Arrays.stream(values())
                .filter(state -> state.wireName.equals(value))
                .findFirst()
                .orElseThrow(InvalidExpressionPackException::new);
    }
}
