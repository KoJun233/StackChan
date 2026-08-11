package com.kj.stackchan.notification;

import org.springframework.http.HttpStatus;

public class NotificationApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public NotificationApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
