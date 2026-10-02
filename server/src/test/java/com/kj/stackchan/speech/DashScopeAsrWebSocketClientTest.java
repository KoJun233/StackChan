package com.kj.stackchan.speech;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashScopeAsrWebSocketClientTest {

    @Test
    void sendsRecordedPcmOnceInOrderedIndependentBoundedChunks() {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendBinary(any(), eq(true))).thenReturn(CompletableFuture.completedFuture(socket));
        byte[] pcm = new byte[6401];
        pcm[0] = 1;
        pcm[3200] = 2;
        pcm[6400] = 3;
        assertThat(DashScopeAsrWebSocketClient.sendRecordedAudio(socket, pcm, () -> false)).isTrue();
        ArgumentCaptor<ByteBuffer> frames = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(socket, times(3)).sendBinary(frames.capture(), eq(true));
        assertThat(frames.getAllValues()).extracting(ByteBuffer::remaining).containsExactly(3200, 3200, 1);
        pcm[0] = 9;
        assertThat(frames.getAllValues().get(0).get(0)).isEqualTo((byte) 1);
        assertThat(frames.getAllValues().get(1).get(0)).isEqualTo((byte) 2);
        assertThat(frames.getAllValues().get(2).get(0)).isEqualTo((byte) 3);
    }

    @Test
    void stopsSendingAfterAProviderTerminalResult() {
        WebSocket socket = mock(WebSocket.class);
        AtomicInteger sends = new AtomicInteger();
        when(socket.sendBinary(any(), eq(true))).thenAnswer(invocation -> {
            sends.incrementAndGet();
            return CompletableFuture.completedFuture(socket);
        });
        assertThat(DashScopeAsrWebSocketClient.sendRecordedAudio(socket, new byte[16000],
                () -> sends.get() >= 1)).isFalse();
        verify(socket).sendBinary(any(), eq(true));
    }
}
