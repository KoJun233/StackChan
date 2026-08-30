package com.kj.stackchan.device;

public record DeviceBodyDiagnostics(
        boolean bodyMotionSupported,
        boolean bodyTouchSupported,
        boolean proximitySupported,
        boolean ambientLightSupported,
        boolean servoFeedbackSupported,
        boolean calibrated,
        boolean present,
        String ambientLight,
        String motionState,
        String lastFailureCode,
        long failureCount
) {
}
