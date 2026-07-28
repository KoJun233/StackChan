package com.kj.stackchan.expression;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ExpressionPackCompiler {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 240;
    public static final int MAX_IMAGE_SIZE = 384 * 1024;
    public static final int MAX_ARTIFACT_SIZE = 1536 * 1024;
    public static final byte[] MAGIC = {'S', 'C', 'E', 'P', 'K', 'G', '1', 0};
    private static final int HEADER_SIZE = 16;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final ObjectMapper objectMapper;

    public ExpressionPackCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedExpressionPack compile(Map<ExpressionState, byte[]> inputImages) {
        if (inputImages == null || inputImages.size() != ExpressionState.values().length) {
            throw new InvalidExpressionPackException();
        }
        EnumMap<ExpressionState, byte[]> images = new EnumMap<>(ExpressionState.class);
        EnumMap<ExpressionState, String> hashes = new EnumMap<>(ExpressionState.class);
        List<ManifestEntry> entries = new ArrayList<>();
        int payloadOffset = 0;
        for (ExpressionState state : ExpressionState.values()) {
            byte[] image = inputImages.get(state);
            validatePng(image);
            byte[] copy = image.clone();
            String hash = sha256(copy);
            images.put(state, copy);
            hashes.put(state, hash);
            entries.add(new ManifestEntry(
                    state.wireName(), "png", WIDTH, HEIGHT, payloadOffset, copy.length, hash
            ));
            payloadOffset = Math.addExact(payloadOffset, copy.length);
        }

        byte[] manifest;
        try {
            manifest = objectMapper.writeValueAsBytes(new Manifest(1, WIDTH, HEIGHT, entries));
        } catch (JsonProcessingException exception) {
            throw new InvalidExpressionPackException();
        }
        if (manifest.length > 16 * 1024) {
            throw new InvalidExpressionPackException();
        }
        int artifactSize = Math.addExact(HEADER_SIZE + manifest.length, payloadOffset);
        if (artifactSize > MAX_ARTIFACT_SIZE) {
            throw new InvalidExpressionPackException();
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(artifactSize);
            output.write(MAGIC);
            output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(1)
                    .putInt(manifest.length)
                    .array());
            output.write(manifest);
            for (ExpressionState state : ExpressionState.values()) {
                output.write(images.get(state));
            }
            byte[] artifact = output.toByteArray();
            return new GeneratedExpressionPack(artifact, sha256(artifact), images, hashes);
        } catch (IOException exception) {
            throw new InvalidExpressionPackException();
        }
    }

    static void validatePng(byte[] image) {
        if (image == null || image.length < 33 || image.length > MAX_IMAGE_SIZE) {
            throw new InvalidExpressionPackException();
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (image[index] != PNG_SIGNATURE[index]) {
                throw new InvalidExpressionPackException();
            }
        }
        if (image[12] != 'I' || image[13] != 'H' || image[14] != 'D' || image[15] != 'R') {
            throw new InvalidExpressionPackException();
        }
        ByteBuffer dimensions = ByteBuffer.wrap(image, 16, 8).order(ByteOrder.BIG_ENDIAN);
        if (dimensions.getInt() != WIDTH || dimensions.getInt() != HEIGHT) {
            throw new InvalidExpressionPackException();
        }
        int bitDepth = Byte.toUnsignedInt(image[24]);
        int colorType = Byte.toUnsignedInt(image[25]);
        if (bitDepth != 8 || (colorType != 2 && colorType != 3 && colorType != 6)) {
            throw new InvalidExpressionPackException();
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null || decoded.getWidth() != WIDTH || decoded.getHeight() != HEIGHT) {
                throw new InvalidExpressionPackException();
            }
        } catch (IOException exception) {
            throw new InvalidExpressionPackException();
        }
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Manifest(int version, int width, int height, List<ManifestEntry> states) {
    }

    private record ManifestEntry(
            String state,
            String format,
            int width,
            int height,
            int offset,
            int length,
            String sha256
    ) {
    }
}
