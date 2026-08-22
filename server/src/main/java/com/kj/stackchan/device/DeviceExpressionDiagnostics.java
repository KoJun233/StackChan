package com.kj.stackchan.device;

public record DeviceExpressionDiagnostics(
        int targetFps,
        int actualFps,
        int drawTimeUs,
        int transferTimeUs,
        int displayLockWaitUs,
        long droppedFrames,
        long audioUnderruns,
        long minimumFreeHeap,
        String activeLayer,
        String degradeReason,
        boolean dynamicRenderer,
        boolean imuSupported,
        boolean proximitySupported
) { }
