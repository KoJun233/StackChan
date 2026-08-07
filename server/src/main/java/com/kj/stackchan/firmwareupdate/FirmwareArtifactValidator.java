package com.kj.stackchan.firmwareupdate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class FirmwareArtifactValidator {

    static final int MAX_ARTIFACT_SIZE = 3 * 1024 * 1024;
    private static final int IMAGE_HEADER_SIZE = 24;
    private static final int SEGMENT_HEADER_SIZE = 8;
    private static final int APP_DESCRIPTION_OFFSET = IMAGE_HEADER_SIZE + SEGMENT_HEADER_SIZE;
    private static final int APP_DESCRIPTION_MAGIC = 0xABCD5432;
    private static final int VERSION_OFFSET = APP_DESCRIPTION_OFFSET + 16;
    private static final int PROJECT_NAME_OFFSET = APP_DESCRIPTION_OFFSET + 48;
    private static final int FIELD_SIZE = 32;
    private static final String EXPECTED_PROJECT_NAME = "stackchan_firmware";
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,31}");

    ValidatedFirmwareArtifact validate(byte[] bytes, String requestedVersion) {
        String version = requestedVersion == null ? "" : requestedVersion.strip();
        if (bytes == null || bytes.length < PROJECT_NAME_OFFSET + FIELD_SIZE ||
                bytes.length > MAX_ARTIFACT_SIZE || !VERSION_PATTERN.matcher(version).matches() ||
                Byte.toUnsignedInt(bytes[0]) != 0xE9 || Byte.toUnsignedInt(bytes[1]) == 0 ||
                Byte.toUnsignedInt(bytes[1]) > 16 || readLittleEndianInt(bytes, APP_DESCRIPTION_OFFSET) != APP_DESCRIPTION_MAGIC) {
            throw new InvalidFirmwareUpdateException();
        }
        String embeddedVersion = readCString(bytes, VERSION_OFFSET, FIELD_SIZE);
        String projectName = readCString(bytes, PROJECT_NAME_OFFSET, FIELD_SIZE);
        if (!version.equals(embeddedVersion) || !EXPECTED_PROJECT_NAME.equals(projectName)) {
            throw new InvalidFirmwareUpdateException();
        }
        return new ValidatedFirmwareArtifact(version, projectName, sha256(bytes), bytes.clone());
    }

    private int readLittleEndianInt(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) |
                (Byte.toUnsignedInt(bytes[offset + 1]) << 8) |
                (Byte.toUnsignedInt(bytes[offset + 2]) << 16) |
                (Byte.toUnsignedInt(bytes[offset + 3]) << 24);
    }

    private String readCString(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            int value = Byte.toUnsignedInt(bytes[end]);
            if (value < 0x21 || value > 0x7e) {
                throw new InvalidFirmwareUpdateException();
            }
            end++;
        }
        if (end == offset || end == offset + length) {
            throw new InvalidFirmwareUpdateException();
        }
        for (int index = end; index < offset + length; index++) {
            if (bytes[index] != 0) {
                throw new InvalidFirmwareUpdateException();
            }
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
