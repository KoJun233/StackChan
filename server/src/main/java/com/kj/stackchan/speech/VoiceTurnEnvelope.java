package com.kj.stackchan.speech;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class VoiceTurnEnvelope {

    public static final byte[] MAGIC = {'S', 'C', 'V', '1'};

    private final ObjectMapper objectMapper;

    public VoiceTurnEnvelope(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] encode(VoiceTurnService.VoiceTurnResult result) {
        byte[] metadata = metadata(result);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(8 + metadata.length + result.wavAudio().length);
            DataOutputStream data = new DataOutputStream(output);
            data.write(MAGIC);
            data.writeInt(metadata.length);
            data.write(metadata);
            data.write(result.wavAudio());
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Voice response could not be encoded", exception);
        }
    }

    private byte[] metadata(VoiceTurnService.VoiceTurnResult result) {
        try {
            return objectMapper.writeValueAsBytes(new VoiceTurnMetadata(result.transcript(), result.reply()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Voice metadata could not be encoded", exception);
        }
    }

    private record VoiceTurnMetadata(String transcript, String reply) {
    }
}
