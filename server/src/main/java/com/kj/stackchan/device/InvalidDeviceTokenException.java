package com.kj.stackchan.device;

public class InvalidDeviceTokenException extends RuntimeException {

    public InvalidDeviceTokenException(String message) {
        super(message);
    }

    public InvalidDeviceTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
