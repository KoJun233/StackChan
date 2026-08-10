package com.kj.stackchan.firmwareupdate;

record ValidatedFirmwareArtifact(String version, String projectName, String sha256, byte[] bytes) {
}
