package com.kj.stackchan.weather;

public class WorkdayWeatherUnavailableException extends RuntimeException {

    private final WorkdayWeatherFailureCode failureCode;

    public WorkdayWeatherUnavailableException(WorkdayWeatherFailureCode failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public WorkdayWeatherUnavailableException(
            WorkdayWeatherFailureCode failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public WorkdayWeatherFailureCode getFailureCode() {
        return failureCode;
    }
}
