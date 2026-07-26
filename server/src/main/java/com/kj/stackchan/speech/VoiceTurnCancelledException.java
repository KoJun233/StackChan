package com.kj.stackchan.speech;

public class VoiceTurnCancelledException extends RuntimeException {

    public VoiceTurnCancelledException() {
        super("Voice turn cancelled");
    }
}
