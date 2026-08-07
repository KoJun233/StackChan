package com.kj.stackchan.firmwareupdate;

public class FirmwareUpdateNotFoundException extends RuntimeException {

    public FirmwareUpdateNotFoundException() {
        super("Firmware release or update job was not found");
    }
}
