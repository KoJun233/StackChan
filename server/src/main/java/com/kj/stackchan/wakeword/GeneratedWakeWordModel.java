package com.kj.stackchan.wakeword;

public record GeneratedWakeWordModel(String modelName, String sha256, byte[] artifact) {

    public GeneratedWakeWordModel {
        artifact = artifact.clone();
    }

    @Override
    public byte[] artifact() {
        return artifact.clone();
    }
}
