package com.kj.stackchan.weather;

public class InvalidWorkdayWeatherException extends RuntimeException {

    public InvalidWorkdayWeatherException(String message) {
        super(message);
    }

    public InvalidWorkdayWeatherException(String message, Throwable cause) {
        super(message, cause);
    }
}
