package com.kj.stackchan.speech;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class VoiceTurnStreamEnvelope {

    public static final byte[] MAGIC = {'S', 'C', 'V', '2'};
    public static final int FRAME_START = 1;
    public static final int FRAME_AUDIO = 2;
    public static final int FRAME_COMPLETE = 3;
    public static final int FRAME_ERROR = 4;
    static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;
    static final int MAX_METADATA_BYTES = 8192;
    private static final Set<String> ERROR_CODES = Set.of(
            "no_speech", "cancelled", "llm_unavailable", "speech_unavailable", "internal_error"
    );

    private final ObjectMapper objectMapper;

    public VoiceTurnStreamEnvelope(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Writer writer(OutputStream output) {
        return new Writer(output);
    }

    public final class Writer implements VoiceTurnSegmentSink {

        private final DataOutputStream output;
        private boolean magicWritten;
        private boolean started;
        private boolean finished;
        private int nextSequence;

        private Writer(OutputStream output) {
            if (output == null) throw new IllegalArgumentException("Voice stream output is required");
            this.output = new DataOutputStream(output);
        }

        @Override
        public void start(String transcript) {
            if (started || finished || transcript == null || transcript.isBlank()) {
                throw new IllegalStateException("Voice stream start frame is invalid");
            }
            writeFrame(FRAME_START, json(new StartMetadata(transcript)));
            started = true;
        }

        @Override
        public void audio(int sequence, byte[] wavAudio) {
            if (!started || finished || sequence != nextSequence
                    || sequence >= VoiceReplySegmenter.MAX_SEGMENTS || wavAudio == null
                    || wavAudio.length < 44 || wavAudio.length > MAX_AUDIO_BYTES) {
                throw new IllegalStateException("Voice stream audio frame is invalid");
            }
            try {
                ensureMagic();
                output.writeByte(FRAME_AUDIO);
                output.writeInt(Integer.BYTES + wavAudio.length);
                output.writeInt(sequence);
                output.write(wavAudio);
                output.flush();
                nextSequence++;
            } catch (IOException exception) {
                throw new VoiceTurnClientDisconnectedException(exception);
            }
        }

        @Override
        public void complete(int segmentCount) {
            if (!started || finished || segmentCount != nextSequence || segmentCount < 1
                    || segmentCount > VoiceReplySegmenter.MAX_SEGMENTS) {
                throw new IllegalStateException("Voice stream completion frame is invalid");
            }
            writeFrame(FRAME_COMPLETE, json(new CompleteMetadata(segmentCount)));
            finished = true;
        }

        public void error(String code) {
            if (finished) return;
            if (!ERROR_CODES.contains(code)) throw new IllegalArgumentException("Voice stream error code is invalid");
            writeFrame(FRAME_ERROR, json(new ErrorMetadata(code)));
            finished = true;
        }

        private byte[] json(Object value) {
            try {
                return objectMapper.writeValueAsBytes(value);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Voice stream metadata could not be encoded", exception);
            }
        }

        private void writeFrame(int type, byte[] payload) {
            if (type != FRAME_AUDIO && payload.length > MAX_METADATA_BYTES) {
                throw new IllegalStateException("Voice stream metadata is too large");
            }
            try {
                ensureMagic();
                output.writeByte(type);
                output.writeInt(payload.length);
                output.write(payload);
                output.flush();
            } catch (IOException exception) {
                throw new VoiceTurnClientDisconnectedException(exception);
            }
        }

        private void ensureMagic() throws IOException {
            if (!magicWritten) {
                output.write(MAGIC);
                magicWritten = true;
            }
        }
    }

    private record StartMetadata(String transcript) { }

    private record CompleteMetadata(int segmentCount) { }

    private record ErrorMetadata(String code) { }
}
