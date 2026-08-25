package com.kj.stackchan.calendar;

public class ICloudCalendarUnavailableException extends RuntimeException {

    private final ICloudCalendarFailureCode failureCode;

    public ICloudCalendarUnavailableException(ICloudCalendarFailureCode failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public ICloudCalendarUnavailableException(
            ICloudCalendarFailureCode failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public ICloudCalendarFailureCode getFailureCode() {
        return failureCode;
    }
}
