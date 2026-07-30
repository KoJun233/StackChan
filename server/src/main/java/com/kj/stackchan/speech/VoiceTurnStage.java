package com.kj.stackchan.speech;

import java.util.EnumSet;
import java.util.Set;

public enum VoiceTurnStage {
    WAKE_DETECTED,
    TOUCH_STARTED,
    LISTENING,
    FOLLOW_UP_LISTENING,
    SPEECH_CAPTURED,
    UPLOAD_STARTED,
    REQUEST_RECEIVED,
    ASR_COMPLETED,
    LLM_COMPLETED,
    TTS_COMPLETED,
    PLAYBACK_STARTED,
    PLAYBACK_COMPLETED,
    FOLLOW_UP_TIMEOUT,
    CONVERSATION_ENDED,
    LISTENING_RESUMED,
    CANCELLED,
    FAILED;

    private static final Set<VoiceTurnStage> DEVICE_STAGES = EnumSet.of(
            WAKE_DETECTED,
            TOUCH_STARTED,
            LISTENING,
            FOLLOW_UP_LISTENING,
            SPEECH_CAPTURED,
            UPLOAD_STARTED,
            PLAYBACK_STARTED,
            PLAYBACK_COMPLETED,
            FOLLOW_UP_TIMEOUT,
            CONVERSATION_ENDED,
            LISTENING_RESUMED,
            CANCELLED,
            FAILED
    );

    public boolean isDeviceStage() {
        return DEVICE_STAGES.contains(this);
    }
}
