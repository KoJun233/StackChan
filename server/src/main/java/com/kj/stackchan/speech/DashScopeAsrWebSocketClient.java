package com.kj.stackchan.speech;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class DashScopeAsrWebSocketClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);
    private static final int PCM_CHUNK_BYTES = 3200;
    private static final long PCM_CHUNK_INTERVAL_MILLIS = 100;

    private final ObjectMapper objectMapper;

    DashScopeAsrWebSocketClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String transcribe(URI endpoint, String apiKey, String workspaceId, String model, byte[] pcmAudio) {
        String taskId = UUID.randomUUID().toString();
        Listener listener = new Listener(objectMapper, taskId, model, pcmAudio);
        WebSocket webSocket = null;
        try {
            try {
                webSocket = HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build()
                        .newWebSocketBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("X-DashScope-WorkSpace", workspaceId)
                        .header("User-Agent", "StackChan-Companion/1")
                        .buildAsync(endpoint, listener)
                        .get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException exception) {
                throw handshakeFailure(exception.getCause());
            } catch (TimeoutException exception) {
                throw new SpeechProviderUnavailableException("dashscope_asr_handshake_timeout", exception);
            }
            try {
                return listener.result().get(PROVIDER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).trim();
            } catch (ExecutionException exception) {
                throw providerFailure("dashscope_asr_result", exception.getCause());
            } catch (TimeoutException exception) {
                throw new SpeechProviderUnavailableException("dashscope_asr_result_timeout", exception);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpeechProviderUnavailableException("dashscope_asr_interrupted", exception);
        } catch (SpeechProviderUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SpeechProviderUnavailableException("dashscope_asr_transport", exception);
        } catch (Exception exception) {
            throw new SpeechProviderUnavailableException("dashscope_asr_transport", exception);
        } finally {
            if (webSocket != null
                    && (!listener.result().isDone() || listener.result().isCompletedExceptionally())) {
                webSocket.abort();
            }
        }
    }

    private static SpeechProviderUnavailableException handshakeFailure(Throwable cause) {
        Throwable root = unwrap(cause);
        if (root instanceof WebSocketHandshakeException handshakeException) {
            return new SpeechProviderUnavailableException(
                    SpeechProviderUnavailableException.httpDiagnosticCode(
                            "dashscope_asr_handshake", handshakeException.getResponse().statusCode()
                    ),
                    root
            );
        }
        return providerFailure("dashscope_asr_handshake", root);
    }

    private static SpeechProviderUnavailableException providerFailure(String stage, Throwable cause) {
        Throwable root = unwrap(cause);
        if (root instanceof SpeechProviderUnavailableException unavailableException) {
            return unavailableException;
        }
        return new SpeechProviderUnavailableException(stage, root);
    }

    private static Throwable unwrap(Throwable cause) {
        Throwable current = cause;
        while (current != null
                && current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof ExecutionException)) {
            current = current.getCause();
        }
        return current == null ? new IllegalStateException("Missing provider failure") : current;
    }

    private static final class Listener implements WebSocket.Listener {

        private final ObjectMapper objectMapper;
        private final String taskId;
        private final String model;
        private final byte[] pcmAudio;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final StringBuilder textFrame = new StringBuilder();
        private final StringBuilder transcript = new StringBuilder();
        private final AtomicBoolean audioStarted = new AtomicBoolean();

        private Listener(ObjectMapper objectMapper, String taskId, String model, byte[] pcmAudio) {
            this.objectMapper = objectMapper;
            this.taskId = taskId;
            this.model = model;
            this.pcmAudio = pcmAudio;
        }

        CompletableFuture<String> result() {
            return result;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            webSocket.sendText(DashScopeAsrProtocol.runTask(objectMapper, taskId, model), true)
                    .exceptionally(exception -> {
                        fail(exception);
                        return null;
                    });
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textFrame.append(data);
            if (last) {
                String message = textFrame.toString();
                textFrame.setLength(0);
                try {
                    handleEvent(webSocket, DashScopeAsrProtocol.parseServerEvent(objectMapper, message));
                } catch (RuntimeException exception) {
                    fail(exception);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!result.isDone()) {
                result.completeExceptionally(
                        new SpeechProviderUnavailableException("dashscope_asr_closed")
                );
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(new SpeechProviderUnavailableException("dashscope_asr_transport", error));
        }

        private void handleEvent(WebSocket webSocket, DashScopeAsrProtocol.ServerEvent event) {
            switch (event.event()) {
                case "task-started" -> sendAudio(webSocket);
                case "result-generated" -> {
                    if (event.sentenceEnd() && !event.text().isBlank()) {
                        transcript.append(event.text());
                    }
                }
                case "task-finished" -> {
                    result.complete(transcript.toString());
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "complete");
                }
                case "task-failed" -> fail(
                        new SpeechProviderUnavailableException("dashscope_asr_task_failed")
                );
                default -> {
                    // Unknown informational events are ignored while the documented task continues.
                }
            }
        }

        private void sendAudio(WebSocket webSocket) {
            if (!audioStarted.compareAndSet(false, true)) {
                return;
            }
            Thread.startVirtualThread(() -> {
                try {
                    for (int offset = 0; offset < pcmAudio.length; offset += PCM_CHUNK_BYTES) {
                        if (result.isDone()) {
                            return;
                        }
                        int end = Math.min(offset + PCM_CHUNK_BYTES, pcmAudio.length);
                        byte[] chunk = Arrays.copyOfRange(pcmAudio, offset, end);
                        webSocket.sendBinary(ByteBuffer.wrap(chunk), true).join();
                        if (end < pcmAudio.length) {
                            Thread.sleep(PCM_CHUNK_INTERVAL_MILLIS);
                        }
                    }
                    webSocket.sendText(DashScopeAsrProtocol.finishTask(objectMapper, taskId), true).join();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(new SpeechProviderUnavailableException("dashscope_asr_audio_interrupted", exception));
                } catch (RuntimeException exception) {
                    fail(new SpeechProviderUnavailableException("dashscope_asr_audio_send", exception));
                }
            });
        }

        private void fail(Throwable error) {
            result.completeExceptionally(error);
        }
    }
}
