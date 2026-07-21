package com.kj.stackchan.device;

public class InvalidDeviceRefreshCredentialException extends RuntimeException {

    public InvalidDeviceRefreshCredentialException() {
        super("Device refresh credential is invalid");
    }
}
