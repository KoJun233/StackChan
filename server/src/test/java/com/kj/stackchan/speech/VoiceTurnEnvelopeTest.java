package com.kj.stackchan.speech;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceTurnEnvelopeTest {

    @Test
    void encodesMagicMetadataLengthAndWavBytes() {
        byte[] wav = {1, 2, 3, 4};
        byte[] encoded = new VoiceTurnEnvelope(new ObjectMapper()).encode(
                new VoiceTurnService.VoiceTurnResult("你好", "去拿外卖吧", wav)
        );

        assertThat(encoded).startsWith(VoiceTurnEnvelope.MAGIC);
        int metadataLength = ByteBuffer.wrap(encoded, 4, 4).getInt();
        String metadata = new String(encoded, 8, metadataLength, StandardCharsets.UTF_8);
        assertThat(metadata).contains("你好").contains("去拿外卖吧");
        assertThat(encoded).endsWith(wav);
    }
}
