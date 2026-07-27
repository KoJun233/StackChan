package com.kj.stackchan.interaction;

public class InvalidInteractionSettingsException extends RuntimeException {

    public InvalidInteractionSettingsException(String message) {
        super(message);
    }

    public InvalidInteractionSettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
