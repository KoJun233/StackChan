package com.kj.stackchan.firmwareupdate;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirmwareArtifactValidatorTest {

    private final FirmwareArtifactValidator validator = new FirmwareArtifactValidator();

    @Test
    void acceptsOnlyMatchingStackChanApplicationImages() {
        byte[] artifact = image("ops-002", "stackchan_firmware");

        ValidatedFirmwareArtifact validated = validator.validate(artifact, "ops-002");

        assertThat(validated.version()).isEqualTo("ops-002");
        assertThat(validated.projectName()).isEqualTo("stackchan_firmware");
        assertThat(validated.sha256()).hasSize(64);
        assertThat(validated.bytes()).isNotSameAs(artifact).containsExactly(artifact);
    }

    @Test
    void rejectsVersionMismatchAndForeignProjects() {
        assertThatThrownBy(() -> validator.validate(image("ops-002", "stackchan_firmware"), "ops-003"))
                .isInstanceOf(InvalidFirmwareUpdateException.class);
        assertThatThrownBy(() -> validator.validate(image("ops-002", "other_project"), "ops-002"))
                .isInstanceOf(InvalidFirmwareUpdateException.class);
    }

    @Test
    void rejectsMalformedOrOversizedImages() {
        assertThatThrownBy(() -> validator.validate(new byte[256], "ops-002"))
                .isInstanceOf(InvalidFirmwareUpdateException.class);
        assertThatThrownBy(() -> validator.validate(
                new byte[FirmwareArtifactValidator.MAX_ARTIFACT_SIZE + 1], "ops-002"))
                .isInstanceOf(InvalidFirmwareUpdateException.class);
    }

    private byte[] image(String version, String projectName) {
        byte[] bytes = new byte[256];
        bytes[0] = (byte)0xe9;
        bytes[1] = 1;
        bytes[32] = 0x32;
        bytes[33] = 0x54;
        bytes[34] = (byte)0xcd;
        bytes[35] = (byte)0xab;
        copyAscii(bytes, 48, version);
        copyAscii(bytes, 80, projectName);
        return bytes;
    }

    private void copyAscii(byte[] bytes, int offset, String value) {
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, bytes, offset, source.length);
    }
}
