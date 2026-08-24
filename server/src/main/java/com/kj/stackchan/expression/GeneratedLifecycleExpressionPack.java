package com.kj.stackchan.expression;

import java.util.Map;

public record GeneratedLifecycleExpressionPack(
        byte[] artifact,
        String sha256,
        Map<LifecycleClip, Clip> clips
) {
    public GeneratedLifecycleExpressionPack {
        artifact = artifact.clone();
        clips = Map.copyOf(clips);
    }

    public record Clip(byte[] data, String sha256, int frameCount, int frameDelayMs) {
        public Clip {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }
}
