package com.kj.stackchan.expression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class LifecycleExpressionPackCompiler {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 160;
    public static final int MAX_CLIP_SIZE = 384 * 1024;
    public static final int MAX_FRAME_COUNT = 120;
    public static final int MIN_FRAME_DELAY_MS = 16;
    public static final int MAX_FRAME_DELAY_MS = 100;
    public static final int MAX_DURATION_MS = 5_000;
    public static final byte[] MAGIC = {'S', 'C', 'E', 'P', 'K', 'G', '2', 0};
    private static final int HEADER_SIZE = 16;

    private final ObjectMapper objectMapper;

    public LifecycleExpressionPackCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedLifecycleExpressionPack compile(
            Map<LifecycleClip, byte[]> inputClips,
            Map<LifecycleClip, Integer> frameDelays
    ) {
        if (inputClips == null || inputClips.isEmpty() || inputClips.size() > LifecycleClip.values().length) {
            throw new InvalidExpressionPackException();
        }
        EnumMap<LifecycleClip, GeneratedLifecycleExpressionPack.Clip> clips =
                new EnumMap<>(LifecycleClip.class);
        List<ManifestEntry> entries = new ArrayList<>();
        int payloadOffset = 0;
        for (LifecycleClip clip : LifecycleClip.values()) {
            byte[] input = inputClips.get(clip);
            if (input == null) continue;
            int frameDelayMs = frameDelays == null || frameDelays.get(clip) == null
                    ? 33 : frameDelays.get(clip);
            EafMetadata metadata = inspectEaf(input, frameDelayMs);
            byte[] data = input.clone();
            String sha256 = ExpressionPackCompiler.sha256(data);
            clips.put(clip, new GeneratedLifecycleExpressionPack.Clip(
                    data, sha256, metadata.frameCount(), frameDelayMs));
            entries.add(new ManifestEntry(
                    clip.wireName(), "eaf-rle4", WIDTH, HEIGHT, metadata.frameCount(),
                    frameDelayMs, payloadOffset, data.length, sha256));
            payloadOffset = Math.addExact(payloadOffset, data.length);
        }
        if (clips.size() != inputClips.size()) throw new InvalidExpressionPackException();

        byte[] manifest;
        try {
            manifest = objectMapper.writeValueAsBytes(new Manifest(
                    2, "lifecycle_eaf", WIDTH, HEIGHT, entries));
        } catch (JsonProcessingException exception) {
            throw new InvalidExpressionPackException();
        }
        if (manifest.length == 0 || manifest.length > 16 * 1024) {
            throw new InvalidExpressionPackException();
        }
        int artifactSize = Math.addExact(HEADER_SIZE + manifest.length, payloadOffset);
        if (artifactSize > ExpressionPackCompiler.MAX_ARTIFACT_SIZE) {
            throw new InvalidExpressionPackException();
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(artifactSize);
            output.write(MAGIC);
            output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(2).putInt(manifest.length).array());
            output.write(manifest);
            for (LifecycleClip clip : LifecycleClip.values()) {
                GeneratedLifecycleExpressionPack.Clip value = clips.get(clip);
                if (value != null) output.write(value.data());
            }
            byte[] artifact = output.toByteArray();
            return new GeneratedLifecycleExpressionPack(
                    artifact, ExpressionPackCompiler.sha256(artifact), clips);
        } catch (IOException exception) {
            throw new InvalidExpressionPackException();
        }
    }

    static EafMetadata inspectEaf(byte[] data, int frameDelayMs) {
        if (data == null || data.length < 24 || data.length > MAX_CLIP_SIZE ||
                frameDelayMs < MIN_FRAME_DELAY_MS || frameDelayMs > MAX_FRAME_DELAY_MS ||
                Byte.toUnsignedInt(data[0]) != 0x89 || data[1] != 'E' || data[2] != 'A' || data[3] != 'F') {
            throw new InvalidExpressionPackException();
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int frames = buffer.getInt(4);
        long expectedChecksum = Integer.toUnsignedLong(buffer.getInt(8));
        long payloadLength = Integer.toUnsignedLong(buffer.getInt(12));
        if (frames < 1 || frames > MAX_FRAME_COUNT || payloadLength != data.length - 16L ||
                (long) frames * frameDelayMs > MAX_DURATION_MS || 16L + frames * 8L >= data.length) {
            throw new InvalidExpressionPackException();
        }
        long checksum = 0;
        for (int index = 16; index < data.length; index++) checksum = (checksum + Byte.toUnsignedInt(data[index])) & 0xffffffffL;
        if (checksum != expectedChecksum) throw new InvalidExpressionPackException();

        int frameRegion = 16 + frames * 8;
        int expectedOffset = 0;
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            long frameSize = Integer.toUnsignedLong(buffer.getInt(16 + frameIndex * 8));
            long frameOffset = Integer.toUnsignedLong(buffer.getInt(20 + frameIndex * 8));
            if (frameOffset != expectedOffset || frameSize < 86 || frameSize > data.length - frameRegion - frameOffset) {
                throw new InvalidExpressionPackException();
            }
            int start = Math.toIntExact(frameRegion + frameOffset);
            validateRle4Frame(data, start, Math.toIntExact(frameSize));
            expectedOffset = Math.addExact(expectedOffset, Math.toIntExact(frameSize));
        }
        if (frameRegion + expectedOffset != data.length) throw new InvalidExpressionPackException();
        return new EafMetadata(frames);
    }

    private static void validateRle4Frame(byte[] data, int start, int frameSize) {
        int end = start + frameSize;
        if (Byte.toUnsignedInt(data[start]) != 0x5a || Byte.toUnsignedInt(data[start + 1]) != 0x5a ||
                data[start + 2] != '_' || data[start + 3] != 'S' || data[start + 4] != 0 ||
                Byte.toUnsignedInt(data[start + 11]) != 4) {
            throw new InvalidExpressionPackException();
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int width = Short.toUnsignedInt(buffer.getShort(start + 12));
        int height = Short.toUnsignedInt(buffer.getShort(start + 14));
        int blocks = Short.toUnsignedInt(buffer.getShort(start + 16));
        int blockHeight = Short.toUnsignedInt(buffer.getShort(start + 18));
        if (width != WIDTH || height != HEIGHT || blocks < 1 || blocks > HEIGHT ||
                blockHeight < 1 || blockHeight > HEIGHT || (blocks - 1) * blockHeight >= HEIGHT ||
                blocks * blockHeight < HEIGHT) {
            throw new InvalidExpressionPackException();
        }
        int cursor = start + 20 + blocks * 4 + 64;
        if (cursor >= end) throw new InvalidExpressionPackException();
        for (int block = 0; block < blocks; block++) {
            int length = buffer.getInt(start + 20 + block * 4);
            int rows = Math.min(blockHeight, HEIGHT - block * blockHeight);
            int expectedDecoded = WIDTH * rows / 2;
            if (length < 3 || length > end - cursor || Byte.toUnsignedInt(data[cursor]) != 0) {
                throw new InvalidExpressionPackException();
            }
            int decoded = 0;
            for (int index = cursor + 1; index < cursor + length; index += 2) {
                if (index + 1 >= cursor + length) throw new InvalidExpressionPackException();
                int count = Byte.toUnsignedInt(data[index]);
                if (count == 0) throw new InvalidExpressionPackException();
                decoded = Math.addExact(decoded, count);
            }
            if (decoded != expectedDecoded) throw new InvalidExpressionPackException();
            cursor += length;
        }
        if (cursor != end) throw new InvalidExpressionPackException();
    }

    record EafMetadata(int frameCount) {}
    private record Manifest(int version, String kind, int width, int height, List<ManifestEntry> clips) {}
    private record ManifestEntry(String event, String format, int width, int height, int frameCount,
                                 int frameDelayMs, int offset, int length, String sha256) {}
}
