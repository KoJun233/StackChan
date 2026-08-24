package com.kj.stackchan.workday;

public class InvalidWorkdaySettingsException extends RuntimeException {

    public InvalidWorkdaySettingsException(String message) {
        super(message);
    }

    public InvalidWorkdaySettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
