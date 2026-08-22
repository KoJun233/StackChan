package com.kj.stackchan.expression;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleExpressionPackCompilerTest {
    private final LifecycleExpressionPackCompiler compiler =
            new LifecycleExpressionPackCompiler(new ObjectMapper());

    @Test
    void compilesAWhitelistedBoundedRleClipCatalog() {
        byte[] eaf = validEaf();
        GeneratedLifecycleExpressionPack result = compiler.compile(
                Map.of(LifecycleClip.BOOT_APPEAR, eaf, LifecycleClip.WAKE, eaf),
                Map.of(LifecycleClip.BOOT_APPEAR, 33, LifecycleClip.WAKE, 25));

        assertThat(result.artifact()).startsWith(LifecycleExpressionPackCompiler.MAGIC);
        ByteBuffer header = ByteBuffer.wrap(result.artifact(), 8, 8).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(header.getInt()).isEqualTo(2);
        assertThat(header.getInt()).isPositive();
        assertThat(result.clips()).containsOnlyKeys(LifecycleClip.BOOT_APPEAR, LifecycleClip.WAKE);
        assertThat(result.clips().get(LifecycleClip.WAKE).frameDelayMs()).isEqualTo(25);
    }

    @Test
    void rejectsCorruptionUnknownGeometryAndUnboundedDuration() {
        byte[] corrupted = validEaf();
        corrupted[8]++;
        assertThatThrownBy(() -> compiler.compile(
                Map.of(LifecycleClip.BOOT_APPEAR, corrupted), Map.of()))
                .isInstanceOf(InvalidExpressionPackException.class);

        assertThatThrownBy(() -> compiler.compile(
                Map.of(LifecycleClip.BOOT_APPEAR, validEaf()),
                Map.of(LifecycleClip.BOOT_APPEAR, 101)))
                .isInstanceOf(InvalidExpressionPackException.class);
    }

    private static byte[] validEaf() {
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        block.write(0);
        int remaining = 160 * 160 / 2;
        while (remaining > 0) {
            int count = Math.min(255, remaining);
            block.write(count);
            block.write(0x22);
            remaining -= count;
        }
        byte[] blockData = block.toByteArray();
        ByteBuffer frame = ByteBuffer.allocate(20 + 4 + 64 + blockData.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        frame.put((byte) 0x5a).put((byte) 0x5a);
        frame.put((byte) '_').put((byte) 'S').put((byte) 0);
        frame.put("1.0.0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        frame.put((byte) 4).putShort((short) 160).putShort((short) 160);
        frame.putShort((short) 1).putShort((short) 160);
        frame.putInt(blockData.length);
        frame.put(new byte[64]);
        frame.put(blockData);
        byte[] frameData = frame.array();

        ByteBuffer payload = ByteBuffer.allocate(8 + frameData.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(frameData.length).putInt(0).put(frameData);
        byte[] payloadData = payload.array();
        long checksum = 0;
        for (byte value : payloadData) checksum = (checksum + Byte.toUnsignedInt(value)) & 0xffffffffL;
        ByteBuffer eaf = ByteBuffer.allocate(16 + payloadData.length).order(ByteOrder.LITTLE_ENDIAN);
        eaf.put((byte) 0x89).put((byte) 'E').put((byte) 'A').put((byte) 'F');
        eaf.putInt(1).putInt((int) checksum).putInt(payloadData.length).put(payloadData);
        return eaf.array();
    }
}
