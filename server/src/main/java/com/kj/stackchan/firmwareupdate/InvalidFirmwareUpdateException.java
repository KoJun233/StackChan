package com.kj.stackchan.firmwareupdate;

public class InvalidFirmwareUpdateException extends RuntimeException {

    public InvalidFirmwareUpdateException() {
        super("Firmware release or update request is invalid");
    }
}
