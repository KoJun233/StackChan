package com.kj.stackchan.task;

public class PersonalTaskNotFoundException extends RuntimeException {
    public PersonalTaskNotFoundException() {
        super("Personal task not found");
    }
}
