package com.kj.stackchan.llm;

public class InvalidLlmSettingsException extends RuntimeException {

    public InvalidLlmSettingsException(String message) {
        super(message);
    }

    public InvalidLlmSettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
