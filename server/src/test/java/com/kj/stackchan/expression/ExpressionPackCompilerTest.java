package com.kj.stackchan.expression;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionPackCompilerTest {

    private final ExpressionPackCompiler compiler = new ExpressionPackCompiler(new ObjectMapper());

    @Test
    void compilesAllEightDecodedPngStatesIntoAVersionedArtifact() throws Exception {
        Map<ExpressionState, byte[]> images = images(320, 240);

        GeneratedExpressionPack result = compiler.compile(images);

        assertThat(result.artifact()).startsWith(ExpressionPackCompiler.MAGIC);
        ByteBuffer header = ByteBuffer.wrap(result.artifact(), 8, 8).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(header.getInt()).isEqualTo(1);
        assertThat(header.getInt()).isPositive();
        assertThat(result.sha256()).hasSize(64);
        assertThat(result.images()).containsOnlyKeys(ExpressionState.values());
        assertThat(result.imageSha256()).allSatisfy((state, hash) -> assertThat(hash).hasSize(64));
    }

    @Test
    void rejectsMissingWrongSizedAndTruncatedImages() throws Exception {
        Map<ExpressionState, byte[]> missing = images(320, 240);
        missing.remove(ExpressionState.ERROR);

        assertThatThrownBy(() -> compiler.compile(missing))
                .isInstanceOf(InvalidExpressionPackException.class);
        assertThatThrownBy(() -> compiler.compile(images(319, 240)))
                .isInstanceOf(InvalidExpressionPackException.class);

        Map<ExpressionState, byte[]> truncated = images(320, 240);
        byte[] idle = truncated.get(ExpressionState.IDLE);
        truncated.put(ExpressionState.IDLE, Arrays.copyOf(idle, 33));
        assertThatThrownBy(() -> compiler.compile(truncated))
                .isInstanceOf(InvalidExpressionPackException.class);
    }

    private Map<ExpressionState, byte[]> images(int width, int height) throws Exception {
        EnumMap<ExpressionState, byte[]> images = new EnumMap<>(ExpressionState.class);
        for (ExpressionState state : ExpressionState.values()) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xff123456 + state.ordinal());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            images.put(state, output.toByteArray());
        }
        return images;
    }
}
