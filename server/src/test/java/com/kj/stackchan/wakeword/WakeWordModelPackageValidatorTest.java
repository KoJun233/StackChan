package com.kj.stackchan.wakeword;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WakeWordModelPackageValidatorTest {

    private static final String CUSTOM_MODEL = "wn9l_stackchan_custom";

    private final WakeWordModelPackageValidator validator = new WakeWordModelPackageValidator();

    @Test
    void acceptsAContiguousPackageWithCustomAndFactoryFallbackModels() {
        byte[] artifact = packageWithModels(List.of(CUSTOM_MODEL, WakeWordModelPackageValidator.DEFAULT_MODEL_NAME));
        String sha256 = WakeWordModelPackageValidator.sha256(artifact);

        GeneratedWakeWordModel generated = validator.validate(CUSTOM_MODEL, sha256, artifact);

        assertThat(generated.modelName()).isEqualTo(CUSTOM_MODEL);
        assertThat(generated.sha256()).isEqualTo(sha256);
        assertThat(generated.artifact()).isEqualTo(artifact);
    }

    @Test
    void rejectsMissingFallbackAndAcceptsTheFactoryModelAsASelection() {
        byte[] customOnly = packageWithModels(List.of(CUSTOM_MODEL));
        byte[] factoryOnly = packageWithModels(List.of(WakeWordModelPackageValidator.DEFAULT_MODEL_NAME));

        assertThatThrownBy(() -> validator.validate(
                CUSTOM_MODEL, WakeWordModelPackageValidator.sha256(customOnly), customOnly))
                .isInstanceOf(InvalidWakeWordModelPackageException.class)
                .hasMessage("invalid_model_package");
        assertThat(validator.validate(
                WakeWordModelPackageValidator.DEFAULT_MODEL_NAME,
                WakeWordModelPackageValidator.sha256(factoryOnly),
                factoryOnly).modelName()).isEqualTo(WakeWordModelPackageValidator.DEFAULT_MODEL_NAME);
    }

    @Test
    void rejectsHashMismatchAndTrailingUnreferencedBytes() {
        byte[] artifact = packageWithModels(List.of(CUSTOM_MODEL, WakeWordModelPackageValidator.DEFAULT_MODEL_NAME));
        byte[] withTrailingByte = java.util.Arrays.copyOf(artifact, artifact.length + 1);

        assertThatThrownBy(() -> validator.validate(CUSTOM_MODEL, "0".repeat(64), artifact))
                .isInstanceOf(InvalidWakeWordModelPackageException.class);
        assertThatThrownBy(() -> validator.validate(
                CUSTOM_MODEL,
                WakeWordModelPackageValidator.sha256(withTrailingByte),
                withTrailingByte))
                .isInstanceOf(InvalidWakeWordModelPackageException.class)
                .hasMessage("invalid_model_package");
    }

    private byte[] packageWithModels(List<String> modelNames) {
        int fileCount = modelNames.size() * 3;
        int headerSize = Integer.BYTES + modelNames.size() * (32 + Integer.BYTES) +
                fileCount * (32 + Integer.BYTES * 2);
        ByteBuffer buffer = ByteBuffer.allocate(headerSize + fileCount).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(modelNames.size());
        int dataOffset = headerSize;
        for (String modelName : modelNames) {
            putName(buffer, modelName);
            buffer.putInt(3);
            for (String fileName : List.of("_MODEL_INFO_", modelName + "_data", modelName + "_index")) {
                putName(buffer, fileName);
                buffer.putInt(dataOffset++);
                buffer.putInt(1);
            }
        }
        while (buffer.hasRemaining()) {
            buffer.put((byte)0x5a);
        }
        return buffer.array();
    }

    private void putName(ByteBuffer buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > 31) {
            throw new IllegalArgumentException("test name is too long");
        }
        buffer.put(bytes);
        buffer.put(new byte[32 - bytes.length]);
    }
}
