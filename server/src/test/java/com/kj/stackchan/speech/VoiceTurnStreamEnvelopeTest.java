package com.kj.stackchan.speech;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoiceTurnStreamEnvelopeTest {

    @Test
    void writesStrictOrderedFramesAndFlushesEachSegment() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VoiceTurnStreamEnvelope.Writer writer = new VoiceTurnStreamEnvelope(new ObjectMapper()).writer(output);
        writer.start("你好");
        writer.audio(0, new byte[44]);
        writer.audio(1, new byte[48]);
        writer.complete(2);

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(output.toByteArray()));
        assertThat(input.readNBytes(4)).isEqualTo(VoiceTurnStreamEnvelope.MAGIC);
        assertThat(input.readUnsignedByte()).isEqualTo(VoiceTurnStreamEnvelope.FRAME_START);
        assertThat(new ObjectMapper().readTree(input.readNBytes(input.readInt())).path("transcript").asText())
                .isEqualTo("你好");
        assertThat(input.readUnsignedByte()).isEqualTo(VoiceTurnStreamEnvelope.FRAME_AUDIO);
        int firstLength = input.readInt();
        assertThat(input.readInt()).isZero();
        assertThat(input.readNBytes(firstLength - Integer.BYTES)).hasSize(44);
        assertThat(input.readUnsignedByte()).isEqualTo(VoiceTurnStreamEnvelope.FRAME_AUDIO);
        int secondLength = input.readInt();
        assertThat(input.readInt()).isOne();
        assertThat(input.readNBytes(secondLength - Integer.BYTES)).hasSize(48);
        assertThat(input.readUnsignedByte()).isEqualTo(VoiceTurnStreamEnvelope.FRAME_COMPLETE);
        var complete = new ObjectMapper().readTree(input.readNBytes(input.readInt()));
        assertThat(complete.path("segmentCount").asInt()).isEqualTo(2);
        assertThat(input.available()).isZero();
    }

    @Test
    void rejectsOutOfOrderAndDuplicateAudioFrames() {
        VoiceTurnStreamEnvelope.Writer writer = new VoiceTurnStreamEnvelope(new ObjectMapper())
                .writer(new ByteArrayOutputStream());
        writer.start("你好");
        assertThatThrownBy(() -> writer.audio(1, new byte[44]))
                .isInstanceOf(IllegalStateException.class);
        writer.audio(0, new byte[44]);
        assertThatThrownBy(() -> writer.audio(0, new byte[44]))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMoreThanEightAudioFrames() {
        VoiceTurnStreamEnvelope.Writer writer = new VoiceTurnStreamEnvelope(new ObjectMapper())
                .writer(new ByteArrayOutputStream());
        writer.start("你好");
        for (int sequence = 0; sequence < VoiceReplySegmenter.MAX_SEGMENTS; sequence++) {
            writer.audio(sequence, new byte[44]);
        }
        assertThatThrownBy(() -> writer.audio(VoiceReplySegmenter.MAX_SEGMENTS, new byte[44]))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void writesAnAllowlistedErrorAsATerminalFrame() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new VoiceTurnStreamEnvelope(new ObjectMapper()).writer(output).error("llm_unavailable");

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(output.toByteArray()));
        assertThat(input.readNBytes(4)).isEqualTo(VoiceTurnStreamEnvelope.MAGIC);
        assertThat(input.readUnsignedByte()).isEqualTo(VoiceTurnStreamEnvelope.FRAME_ERROR);
        assertThat(new ObjectMapper().readTree(input.readNBytes(input.readInt())).path("code").asText())
                .isEqualTo("llm_unavailable");
        assertThat(input.available()).isZero();
    }
}
