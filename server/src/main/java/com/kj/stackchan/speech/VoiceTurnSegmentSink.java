package com.kj.stackchan.speech;

public interface VoiceTurnSegmentSink {

    void start(String transcript);

    void audio(int sequence, byte[] wavAudio);

    void complete(int segmentCount);
}
