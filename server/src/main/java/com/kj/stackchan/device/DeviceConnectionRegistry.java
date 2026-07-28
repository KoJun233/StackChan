package com.kj.stackchan.device;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.speech.VoiceWakeSensitivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceConnectionRegistry {

    private final ConcurrentMap<UUID, DeviceSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DeviceConnectionRegistry(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    DeviceConnectionRegistry(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    public void register(UUID deviceId, WebSocketSession session) {
        Long credentialVersion = authenticatedCredentialVersion(session);
        Instant expiresAt = authenticatedTokenExpiresAt(session);
        if (credentialVersion == null || expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            closeBestEffort(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession previousSession = null;
        boolean registered = false;
        boolean rejected = false;
        while (!registered && !rejected) {
            DeviceSession candidate = new DeviceSession();
            DeviceSession deviceSession = sessions.putIfAbsent(deviceId, candidate);
            if (deviceSession == null) {
                deviceSession = candidate;
            }
            synchronized (deviceSession.lock) {
                if (sessions.get(deviceId) != deviceSession) {
                    continue;
                }
                if (deviceSession.committedCredentialVersion != null
                        && credentialVersion.longValue() != deviceSession.committedCredentialVersion.longValue()) {
                    rejected = true;
                } else {
                    previousSession = deviceSession.session;
                    deviceSession.session = session;
                    deviceSession.credentialVersion = credentialVersion;
                    deviceSession.expiresAt = expiresAt;
                    registered = true;
                }
            }
        }

        if (rejected) {
            closeBestEffort(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (previousSession != null && previousSession != session) {
            closeBestEffort(previousSession, CloseStatus.NORMAL);
        }
    }

    public void unregister(UUID deviceId, WebSocketSession session) {
        DeviceSession deviceSession = sessions.get(deviceId);
        if (deviceSession == null) {
            return;
        }
        synchronized (deviceSession.lock) {
            if (sessions.get(deviceId) != deviceSession) {
                return;
            }
            if (deviceSession.session == session) {
                detachCurrentSession(deviceId, deviceSession, session);
            }
        }
    }

    public boolean sendStopMotion(UUID deviceId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new StopMotionCommand("stop_motion", UUID.randomUUID().toString()));
        } catch (JsonProcessingException exception) {
            return false;
        }

        return sendPayload(deviceId, payload);
    }

    public boolean sendStopAudio(UUID deviceId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new StopAudioCommand("stop_audio", UUID.randomUUID().toString()));
        } catch (JsonProcessingException exception) {
            return false;
        }
        return sendPayload(deviceId, payload);
    }

    public boolean sendInteractionConfiguration(UUID deviceId, int volumePercent, boolean nightMode) {
        String payload = interactionConfigurationPayload(volumePercent, nightMode);
        return payload != null && sendPayload(deviceId, payload);
    }

    public boolean sendInteractionConfigurationIfActive(
            UUID deviceId,
            WebSocketSession session,
            int volumePercent,
            boolean nightMode
    ) throws IOException {
        String payload = interactionConfigurationPayload(volumePercent, nightMode);
        return payload != null && sendIfActive(deviceId, session, new TextMessage(payload));
    }

    private String interactionConfigurationPayload(int volumePercent, boolean nightMode) {
        try {
            return objectMapper.writeValueAsString(new ConfigureInteractionCommand(
                    "configure_interaction", UUID.randomUUID().toString(), volumePercent, nightMode
            ));
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    public boolean sendReminder(UUID deviceId, UUID reminderId, String commandId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new SpeakReminderCommand(
                    "speak_reminder",
                    commandId,
                    reminderId.toString()
            ));
        } catch (JsonProcessingException exception) {
            return false;
        }
        return sendPayload(deviceId, payload);
    }

    public boolean sendWakeModelInstall(
            UUID deviceId,
            UUID jobId,
            String modelName,
            String sha256,
            int artifactSize,
            String commandId
    ) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new InstallWakeModelCommand(
                    "install_wake_model",
                    commandId,
                    jobId.toString(),
                    modelName,
                    sha256,
                    artifactSize
            ));
        } catch (JsonProcessingException exception) {
            return false;
        }
        return sendPayload(deviceId, payload);
    }

    public boolean sendExpressionPackInstall(
            UUID deviceId,
            UUID packId,
            String sha256,
            int artifactSize,
            String commandId
    ) {
        try {
            return sendPayload(deviceId, objectMapper.writeValueAsString(new InstallExpressionPackCommand(
                    "install_expression_pack",
                    commandId,
                    packId.toString(),
                    sha256,
                    artifactSize
            )));
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    public boolean sendExpressionPackClear(UUID deviceId, String commandId) {
        try {
            return sendPayload(deviceId, objectMapper.writeValueAsString(new ClearExpressionPackCommand(
                    "clear_expression_pack", commandId
            )));
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    public int broadcastVoiceConfiguration(
            VoiceWakeSensitivity wakeSensitivity,
            int speechStartThreshold,
            int speechSilenceThreshold
    ) {
        String payload = voiceConfigurationPayload(
                wakeSensitivity,
                speechStartThreshold,
                speechSilenceThreshold
        );
        if (payload == null) {
            return 0;
        }
        int sent = 0;
        for (UUID deviceId : sessions.keySet()) {
            if (sendPayload(deviceId, payload)) {
                sent++;
            }
        }
        return sent;
    }

    public boolean sendVoiceConfigurationIfActive(
            UUID deviceId,
            WebSocketSession session,
            VoiceWakeSensitivity wakeSensitivity,
            int speechStartThreshold,
            int speechSilenceThreshold
    ) throws IOException {
        String payload = voiceConfigurationPayload(
                wakeSensitivity,
                speechStartThreshold,
                speechSilenceThreshold
        );
        return payload != null && sendIfActive(deviceId, session, new TextMessage(payload));
    }

    private String voiceConfigurationPayload(
            VoiceWakeSensitivity wakeSensitivity,
            int speechStartThreshold,
            int speechSilenceThreshold
    ) {
        if (wakeSensitivity == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new ConfigureVoiceDetectionCommand(
                    "configure_voice_detection",
                    UUID.randomUUID().toString(),
                    wakeSensitivity.name(),
                    speechStartThreshold,
                    speechSilenceThreshold
            ));
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private boolean sendPayload(UUID deviceId, String payload) {
        DeviceSession deviceSession = sessions.get(deviceId);
        if (deviceSession == null) {
            return false;
        }
        WebSocketSession retiredSession = null;
        CloseStatus retiredStatus = CloseStatus.NORMAL;
        boolean sent = false;
        synchronized (deviceSession.lock) {
            if (sessions.get(deviceId) != deviceSession) {
                return false;
            }
            WebSocketSession session = deviceSession.session;
            if (session == null) {
                removeIfUncommitted(deviceId, deviceSession);
                return false;
            }
            CloseStatus authorizationFailure = authorizationFailureStatus(deviceSession, session);
            if (authorizationFailure != null) {
                detachCurrentSession(deviceId, deviceSession, session);
                retiredSession = session;
                retiredStatus = authorizationFailure;
            } else {
                try {
                    synchronized (session) {
                        if (sessions.get(deviceId) != deviceSession || deviceSession.session != session) {
                            return false;
                        }
                        authorizationFailure = authorizationFailureStatus(deviceSession, session);
                        if (authorizationFailure != null) {
                            detachCurrentSession(deviceId, deviceSession, session);
                            retiredSession = session;
                            retiredStatus = authorizationFailure;
                        } else {
                            session.sendMessage(new TextMessage(payload));
                            sent = true;
                        }
                    }
                } catch (IOException | RuntimeException exception) {
                    detachCurrentSession(deviceId, deviceSession, session);
                    retiredSession = session;
                    retiredStatus = CloseStatus.NORMAL;
                }
            }
        }
        if (retiredSession != null) {
            closeBestEffort(retiredSession, retiredStatus);
        }
        return sent;
    }

    public boolean processIfActive(UUID deviceId, WebSocketSession session, Runnable action) {
        DeviceSession deviceSession = sessions.get(deviceId);
        if (deviceSession == null) {
            return false;
        }
        WebSocketSession retiredSession = null;
        CloseStatus retiredStatus = CloseStatus.NORMAL;
        boolean processed = false;
        synchronized (deviceSession.lock) {
            if (sessions.get(deviceId) != deviceSession || deviceSession.session != session) {
                return false;
            }
            CloseStatus authorizationFailure = authorizationFailureStatus(deviceSession, session);
            if (authorizationFailure != null) {
                detachCurrentSession(deviceId, deviceSession, session);
                retiredSession = session;
                retiredStatus = authorizationFailure;
            } else {
                action.run();
                processed = true;
            }
        }
        if (retiredSession != null) {
            closeBestEffort(retiredSession, retiredStatus);
        }
        return processed;
    }

    public boolean sendIfActive(UUID deviceId, WebSocketSession session, TextMessage message) throws IOException {
        DeviceSession deviceSession = sessions.get(deviceId);
        if (deviceSession == null) {
            return false;
        }
        WebSocketSession retiredSession = null;
        CloseStatus retiredStatus = CloseStatus.NORMAL;
        IOException failure = null;
        boolean sent = false;
        synchronized (deviceSession.lock) {
            if (sessions.get(deviceId) != deviceSession || session == null || deviceSession.session != session) {
                return false;
            }
            CloseStatus authorizationFailure = authorizationFailureStatus(deviceSession, session);
            if (authorizationFailure != null) {
                detachCurrentSession(deviceId, deviceSession, session);
                retiredSession = session;
                retiredStatus = authorizationFailure;
            } else {
                try {
                    synchronized (session) {
                        if (sessions.get(deviceId) != deviceSession || deviceSession.session != session) {
                            return false;
                        }
                        authorizationFailure = authorizationFailureStatus(deviceSession, session);
                        if (authorizationFailure != null) {
                            detachCurrentSession(deviceId, deviceSession, session);
                            retiredSession = session;
                            retiredStatus = authorizationFailure;
                        } else {
                            session.sendMessage(message);
                            sent = true;
                        }
                    }
                } catch (IOException exception) {
                    detachCurrentSession(deviceId, deviceSession, session);
                    retiredSession = session;
                    retiredStatus = CloseStatus.NORMAL;
                    failure = exception;
                } catch (RuntimeException exception) {
                    detachCurrentSession(deviceId, deviceSession, session);
                    retiredSession = session;
                    retiredStatus = CloseStatus.NORMAL;
                }
            }
        }
        if (retiredSession != null) {
            closeBestEffort(retiredSession, retiredStatus);
        }
        if (failure != null) {
            throw failure;
        }
        return sent;
    }

    public boolean isConnected(UUID deviceId) {
        DeviceSession deviceSession = sessions.get(deviceId);
        if (deviceSession == null) {
            return false;
        }
        WebSocketSession retiredSession = null;
        CloseStatus retiredStatus = CloseStatus.NORMAL;
        boolean connected = false;
        synchronized (deviceSession.lock) {
            if (sessions.get(deviceId) != deviceSession) {
                return false;
            }
            WebSocketSession session = deviceSession.session;
            if (session == null) {
                removeIfUncommitted(deviceId, deviceSession);
                return false;
            }
            CloseStatus authorizationFailure = authorizationFailureStatus(deviceSession, session);
            if (authorizationFailure == null) {
                connected = true;
            } else {
                detachCurrentSession(deviceId, deviceSession, session);
                retiredSession = session;
                retiredStatus = authorizationFailure;
            }
        }
        if (retiredSession != null) {
            closeBestEffort(retiredSession, retiredStatus);
        }
        return connected;
    }

    public void revokeCredentials(UUID deviceId, long currentCredentialVersion) {
        WebSocketSession revokedSession = null;
        boolean recorded = false;
        while (!recorded) {
            DeviceSession candidate = new DeviceSession();
            DeviceSession deviceSession = sessions.putIfAbsent(deviceId, candidate);
            if (deviceSession == null) {
                deviceSession = candidate;
            }
            synchronized (deviceSession.lock) {
                if (sessions.get(deviceId) != deviceSession) {
                    continue;
                }
                Long committedCredentialVersion = deviceSession.committedCredentialVersion;
                if (committedCredentialVersion == null
                        || currentCredentialVersion >= committedCredentialVersion.longValue()) {
                    deviceSession.committedCredentialVersion = currentCredentialVersion;
                    WebSocketSession session = deviceSession.session;
                    if (session != null && deviceSession.credentialVersion != currentCredentialVersion) {
                        detachCurrentSession(deviceId, deviceSession, session);
                        revokedSession = session;
                    }
                }
                recorded = true;
            }
        }
        if (revokedSession != null) {
            closeBestEffort(revokedSession, CloseStatus.POLICY_VIOLATION);
        }
    }

    int sessionStateCount() {
        int count = 0;
        for (DeviceSession deviceSession : sessions.values()) {
            synchronized (deviceSession.lock) {
                if (deviceSession.session != null) {
                    count++;
                }
            }
        }
        return count;
    }

    int stateRecordCount() {
        return sessions.size();
    }

    private void detachCurrentSession(UUID deviceId, DeviceSession deviceSession, WebSocketSession session) {
        if (deviceSession.session == session) {
            deviceSession.session = null;
            deviceSession.credentialVersion = 0;
            deviceSession.expiresAt = null;
            removeIfUncommitted(deviceId, deviceSession);
        }
    }

    private void removeIfUncommitted(UUID deviceId, DeviceSession deviceSession) {
        if (deviceSession.committedCredentialVersion == null) {
            sessions.remove(deviceId, deviceSession);
        }
    }

    private CloseStatus authorizationFailureStatus(DeviceSession deviceSession, WebSocketSession session) {
        if (deviceSession.expiresAt == null || !deviceSession.expiresAt.isAfter(clock.instant())) {
            return CloseStatus.POLICY_VIOLATION;
        }
        try {
            return session.isOpen() ? null : CloseStatus.NORMAL;
        } catch (RuntimeException exception) {
            return CloseStatus.NORMAL;
        }
    }

    private Long authenticatedCredentialVersion(WebSocketSession session) {
        Object credentialVersion = session.getAttributes().get(
                DeviceWebSocketHandshakeInterceptor.CREDENTIAL_VERSION_ATTRIBUTE
        );
        return credentialVersion instanceof Long version ? version : null;
    }

    private Instant authenticatedTokenExpiresAt(WebSocketSession session) {
        Object expiresAt = session.getAttributes().get(
                DeviceWebSocketHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE
        );
        return expiresAt instanceof Instant expiration ? expiration : null;
    }

    private void closeBestEffort(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException ignored) {
            // Session removal happens before transport cleanup.
        }
    }

    private static final class DeviceSession {

        private final Object lock = new Object();
        private WebSocketSession session;
        private long credentialVersion;
        private Long committedCredentialVersion;
        private Instant expiresAt;
    }

    private record StopMotionCommand(String type, String command_id) {
    }

    private record StopAudioCommand(String type, String command_id) {
    }

    private record ConfigureInteractionCommand(
            String type,
            String command_id,
            int volume_percent,
            boolean night_mode
    ) {
    }

    private record SpeakReminderCommand(String type, String command_id, String reminder_id) {
    }

    private record InstallWakeModelCommand(
            String type,
            String command_id,
            String job_id,
            String model_name,
            String sha256,
            int artifact_size
    ) {
    }

    private record InstallExpressionPackCommand(
            String type,
            String command_id,
            String pack_id,
            String sha256,
            int artifact_size
    ) {
    }

    private record ClearExpressionPackCommand(String type, String command_id) {
    }

    private record ConfigureVoiceDetectionCommand(
            String type,
            String command_id,
            String wake_sensitivity,
            int speech_start_threshold,
            int speech_silence_threshold
    ) {
    }
}
