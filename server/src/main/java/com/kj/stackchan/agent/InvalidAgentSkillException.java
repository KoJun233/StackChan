package com.kj.stackchan.agent;

public class InvalidAgentSkillException extends RuntimeException {
    public InvalidAgentSkillException(String message) {
        super(message);
    }

    public InvalidAgentSkillException(String message, Throwable cause) {
        super(message, cause);
    }
}
