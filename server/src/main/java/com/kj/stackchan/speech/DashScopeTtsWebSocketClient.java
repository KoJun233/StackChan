package com.kj.stackchan.speech;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
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
class DashScopeTtsWebSocketClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;

    private final ObjectMapper objectMapper;

    DashScopeTtsWebSocketClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    byte[] synthesize(
            URI endpoint,
            String apiKey,
            String workspaceId,
            String model,
            String voice,
            String text
    ) {
        String taskId = UUID.randomUUID().toString();
        Listener listener = new Listener(objectMapper, taskId, model, voice, text);
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
                throw new SpeechProviderUnavailableException(
                        "dashscope_tts_ws_handshake_timeout", exception
                );
            }
            try {
                return listener.result().get(PROVIDER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException exception) {
                throw providerFailure("dashscope_tts_ws_result", exception.getCause());
            } catch (TimeoutException exception) {
                throw new SpeechProviderUnavailableException("dashscope_tts_ws_result_timeout", exception);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpeechProviderUnavailableException("dashscope_tts_ws_interrupted", exception);
        } catch (SpeechProviderUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SpeechProviderUnavailableException("dashscope_tts_ws_transport", exception);
        } catch (Exception exception) {
            throw new SpeechProviderUnavailableException("dashscope_tts_ws_transport", exception);
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
                            "dashscope_tts_ws_handshake", handshakeException.getResponse().statusCode()
                    ),
                    root
            );
        }
        return providerFailure("dashscope_tts_ws_handshake", root);
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
        private final String voice;
        private final String text;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final StringBuilder textFrame = new StringBuilder();
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private final AtomicBoolean textSent = new AtomicBoolean();

        private Listener(
                ObjectMapper objectMapper,
                String taskId,
                String model,
                String voice,
                String text
        ) {
            this.objectMapper = objectMapper;
            this.taskId = taskId;
            this.model = model;
            this.voice = voice;
            this.text = text;
        }

        CompletableFuture<byte[]> result() {
            return result;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            webSocket.sendText(
                    DashScopeTtsProtocol.runTask(objectMapper, taskId, model, voice), true
            ).exceptionally(exception -> {
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
                    handleEvent(webSocket, DashScopeTtsProtocol.parseServerEvent(objectMapper, message));
                } catch (RuntimeException exception) {
                    fail(exception);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            synchronized (audio) {
                if (audio.size() + chunk.length > MAX_AUDIO_BYTES) {
                    fail(new SpeechProviderUnavailableException("dashscope_tts_ws_audio_too_large"));
                } else {
                    audio.writeBytes(chunk);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!result.isDone()) {
                result.completeExceptionally(
                        new SpeechProviderUnavailableException("dashscope_tts_ws_closed")
                );
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(new SpeechProviderUnavailableException("dashscope_tts_ws_transport", error));
        }

        private void handleEvent(WebSocket webSocket, DashScopeTtsProtocol.ServerEvent event) {
            switch (event.event()) {
                case "task-started" -> sendText(webSocket);
                case "task-finished" -> {
                    byte[] completedAudio;
                    synchronized (audio) {
                        completedAudio = audio.toByteArray();
                    }
                    result.complete(completedAudio);
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "complete");
                }
                case "task-failed" -> fail(
                        new SpeechProviderUnavailableException("dashscope_tts_ws_task_failed")
                );
                default -> {
                    // Unknown informational events are ignored while the documented task continues.
                }
            }
        }

        private void sendText(WebSocket webSocket) {
            if (!textSent.compareAndSet(false, true)) {
                return;
            }
            webSocket.sendText(
                    DashScopeTtsProtocol.continueTask(objectMapper, taskId, text), true
            ).thenCompose(ignored -> webSocket.sendText(
                    DashScopeTtsProtocol.finishTask(objectMapper, taskId), true
            )).exceptionally(exception -> {
                fail(new SpeechProviderUnavailableException("dashscope_tts_ws_text_send", exception));
                return null;
            });
        }

        private void fail(Throwable error) {
            result.completeExceptionally(error);
        }
    }
}
