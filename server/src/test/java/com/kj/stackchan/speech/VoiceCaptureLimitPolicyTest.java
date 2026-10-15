package com.kj.stackchan.speech;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VoiceCaptureLimitPolicyTest {
    @Test void fullBoundIsConservativeEvenIfTheDeviceStageHasNotArrived() {
        assertThat(VoiceCaptureLimitPolicy.requiresClarification(wav(128000,false),false)).isTrue();
        assertThat(VoiceCaptureLimitPolicy.requiresClarification(wav(128000,true),true)).isTrue();
        assertThat(VoiceCaptureLimitPolicy.requiresClarification(wav(127999,false),false)).isFalse();
    }
    @Test void wavHeaderLengthCannotHideFullLiveAudioAndUnknownInputKeepsExistingValidation() {
        byte[] placeholder=wav(128000,false);
        ByteBuffer.wrap(placeholder).order(ByteOrder.LITTLE_ENDIAN).putInt(40,128000*2+2000);
        assertThat(VoiceCaptureLimitPolicy.requiresClarification(placeholder,true)).isTrue();
        assertThat(VoiceCaptureLimitPolicy.requiresClarification(new byte[64],false)).isFalse();
    }
    private byte[] wav(int samples,boolean live) {
        ByteBuffer b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(b.capacity()-8)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short)1).putShort((short)1).putInt(16000).putInt(32000)
                .putShort((short)2).putShort((short)16).put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .putInt(live?-1:samples*2);
        return b.array();
    }
}
