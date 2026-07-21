package com.kj.stackchan.speech;

public class InvalidSpeechSettingsException extends RuntimeException {

    public InvalidSpeechSettingsException(String message) {
        super(message);
    }

    public InvalidSpeechSettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
