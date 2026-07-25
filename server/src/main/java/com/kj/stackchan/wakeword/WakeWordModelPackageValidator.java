package com.kj.stackchan.wakeword;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class WakeWordModelPackageValidator {

    public static final int MAX_ARTIFACT_SIZE = 1024 * 1024;
    public static final String DEFAULT_MODEL_NAME = "wn9l_histackchan_tts3";
    private static final Pattern MODEL_NAME = Pattern.compile("[a-z0-9_]{1,31}");
    private static final int MAX_MODELS = 5;
    private static final int MAX_FILES_PER_MODEL = 8;
    private static final int FIXED_NAME_SIZE = 32;

    public GeneratedWakeWordModel validate(String expectedModelName, String expectedSha256, byte[] artifact) {
        if (!isModelName(expectedModelName) || artifact == null || artifact.length == 0 ||
                artifact.length > MAX_ARTIFACT_SIZE) {
            throw invalid();
        }
        String actualSha256 = sha256(artifact);
        if (expectedSha256 == null || !actualSha256.equals(expectedSha256.toLowerCase())) {
            throw invalid();
        }

        ByteBuffer buffer = ByteBuffer.wrap(artifact).order(ByteOrder.LITTLE_ENDIAN);
        int modelCount = readPositiveInt(buffer, MAX_MODELS);
        Set<String> modelNames = new HashSet<>();
        List<FileRange> ranges = new ArrayList<>();
        for (int modelIndex = 0; modelIndex < modelCount; modelIndex++) {
            String modelName = readName(buffer);
            if (!isModelName(modelName) || !modelNames.add(modelName)) {
                throw invalid();
            }
            int fileCount = readPositiveInt(buffer, MAX_FILES_PER_MODEL);
            Set<String> fileNames = new HashSet<>();
            boolean metadata = false;
            boolean data = false;
            boolean index = false;
            for (int fileIndex = 0; fileIndex < fileCount; fileIndex++) {
                String fileName = readName(buffer);
                if (fileName.isBlank() || !fileNames.add(fileName) || buffer.remaining() < 8) {
                    throw invalid();
                }
                long start = Integer.toUnsignedLong(buffer.getInt());
                long length = Integer.toUnsignedLong(buffer.getInt());
                if (length == 0 || start + length > artifact.length || start + length < start) {
                    throw invalid();
                }
                ranges.add(new FileRange(start, start + length));
                metadata |= "_MODEL_INFO_".equals(fileName);
                data |= fileName.endsWith("_data");
                index |= fileName.endsWith("_index");
            }
            if (!metadata || !data || !index) {
                throw invalid();
            }
        }

        int headerLength = buffer.position();
        ranges.sort(Comparator.comparingLong(FileRange::start));
        long previousEnd = headerLength;
        for (FileRange range : ranges) {
            if (range.start() != previousEnd) {
                throw invalid();
            }
            previousEnd = range.end();
        }
        if (previousEnd != artifact.length || !modelNames.contains(expectedModelName) ||
                !modelNames.contains(DEFAULT_MODEL_NAME)) {
            throw invalid();
        }
        return new GeneratedWakeWordModel(expectedModelName, actualSha256, artifact);
    }

    public static boolean isModelName(String value) {
        return value != null && MODEL_NAME.matcher(value).matches();
    }

    public static String sha256(byte[] artifact) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int readPositiveInt(ByteBuffer buffer, int maximum) {
        if (buffer.remaining() < Integer.BYTES) {
            throw invalid();
        }
        int value = buffer.getInt();
        if (value <= 0 || value > maximum) {
            throw invalid();
        }
        return value;
    }

    private String readName(ByteBuffer buffer) {
        if (buffer.remaining() < FIXED_NAME_SIZE) {
            throw invalid();
        }
        byte[] bytes = new byte[FIXED_NAME_SIZE];
        buffer.get(bytes);
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            int character = Byte.toUnsignedInt(bytes[length]);
            if (character < 0x21 || character > 0x7e) {
                throw invalid();
            }
            length++;
        }
        for (int index = length; index < bytes.length; index++) {
            if (bytes[index] != 0) {
                throw invalid();
            }
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }

    private InvalidWakeWordModelPackageException invalid() {
        return new InvalidWakeWordModelPackageException();
    }

    private record FileRange(long start, long end) {
    }
}
