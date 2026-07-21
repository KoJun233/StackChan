package com.kj.stackchan.reminder;

public class InvalidReminderException extends RuntimeException {

    public InvalidReminderException(String message) {
        super(message);
    }

    public InvalidReminderException(String message, Throwable cause) {
        super(message, cause);
    }
}
