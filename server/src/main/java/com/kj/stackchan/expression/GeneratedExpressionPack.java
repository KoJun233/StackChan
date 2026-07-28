package com.kj.stackchan.expression;

import java.util.Map;

public record GeneratedExpressionPack(
        byte[] artifact,
        String sha256,
        Map<ExpressionState, byte[]> images,
        Map<ExpressionState, String> imageSha256
) {
    public GeneratedExpressionPack {
        artifact = artifact.clone();
        images = Map.copyOf(images);
        imageSha256 = Map.copyOf(imageSha256);
    }
}
