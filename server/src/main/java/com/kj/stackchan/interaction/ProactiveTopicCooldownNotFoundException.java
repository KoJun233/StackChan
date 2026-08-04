package com.kj.stackchan.interaction;

public class ProactiveTopicCooldownNotFoundException extends RuntimeException {
    public ProactiveTopicCooldownNotFoundException() {
        super("Proactive topic cooldown was not found");
    }
}
