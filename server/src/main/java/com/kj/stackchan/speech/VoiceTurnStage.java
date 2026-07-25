package com.kj.stackchan.speech;

import java.util.EnumSet;
import java.util.Set;

public enum VoiceTurnStage {
    WAKE_DETECTED,
    LISTENING,
    SPEECH_CAPTURED,
    UPLOAD_STARTED,
    REQUEST_RECEIVED,
    ASR_COMPLETED,
    LLM_COMPLETED,
    TTS_COMPLETED,
    PLAYBACK_STARTED,
    PLAYBACK_COMPLETED,
    LISTENING_RESUMED,
    FAILED;

    private static final Set<VoiceTurnStage> DEVICE_STAGES = EnumSet.of(
            WAKE_DETECTED,
            LISTENING,
            SPEECH_CAPTURED,
            UPLOAD_STARTED,
            PLAYBACK_STARTED,
            PLAYBACK_COMPLETED,
            LISTENING_RESUMED,
            FAILED
    );

    public boolean isDeviceStage() {
        return DEVICE_STAGES.contains(this);
    }
}
