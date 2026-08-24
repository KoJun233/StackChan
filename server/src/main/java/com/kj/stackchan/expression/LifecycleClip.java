package com.kj.stackchan.expression;

import java.util.Locale;

public enum LifecycleClip {
    BOOT_APPEAR("boot_appear"),
    WAKE("wake"),
    ROLE_SWITCH("role_switch");

    private final String wireName;

    LifecycleClip(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static LifecycleClip fromWireName(String value) {
        if (value != null) {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            for (LifecycleClip clip : values()) {
                if (clip.wireName.equals(normalized)) return clip;
            }
        }
        throw new InvalidExpressionPackException();
    }
}
