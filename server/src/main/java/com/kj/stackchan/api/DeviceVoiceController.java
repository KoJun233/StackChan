package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.speech.VoiceInputException;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import com.kj.stackchan.speech.VoiceTurnEnvelope;
import com.kj.stackchan.speech.VoiceTurnCancelledException;
import com.kj.stackchan.speech.VoiceTurnClientDisconnectedException;
import com.kj.stackchan.speech.VoiceTurnFailureCode;
import com.kj.stackchan.speech.VoiceTurnStreamEnvelope;
import com.kj.stackchan.speech.VoiceTurnStage;
import com.kj.stackchan.speech.VoiceTurnService;
import com.kj.stackchan.llm.LlmProviderUnavailableException;
import com.kj.stackchan.speech.SpeechProviderUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceVoiceController {

    public static final String VOICE_TURN_ID_HEADER = "X-StackChan-Turn-Id";
    public static final MediaType VOICE_TURN_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.stackchan.voice-turn"
    );
    public static final MediaType VOICE_TURN_STREAM_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.stackchan.voice-turn-stream"
    );
    private static final int MIN_WAV_BYTES = 44;
    private static final int MAX_WAV_BYTES = 512 * 1024;

    private final DeviceHttpAuthenticator authenticator;
    private final VoiceTurnService voiceTurnService;
    private final VoiceTurnEnvelope envelope;
    private final VoiceTurnStreamEnvelope streamEnvelope;
    private final VoiceTurnDiagnosticsService diagnosticsService;

    public DeviceVoiceController(
            DeviceHttpAuthenticator authenticator,
            VoiceTurnService voiceTurnService,
            VoiceTurnEnvelope envelope,
            VoiceTurnStreamEnvelope streamEnvelope,
            VoiceTurnDiagnosticsService diagnosticsService
    ) {
        this.authenticator = authenticator;
        this.voiceTurnService = voiceTurnService;
        this.envelope = envelope;
        this.streamEnvelope = streamEnvelope;
        this.diagnosticsService = diagnosticsService;
    }

    @PostMapping(
            path = "/voice/turn",
            consumes = {"audio/wav", MediaType.APPLICATION_OCTET_STREAM_VALUE},
            produces = "application/vnd.stackchan.voice-turn-stream"
    )
    public ResponseEntity<StreamingResponseBody> streamingVoiceTurn(
            HttpServletRequest request,
            @RequestHeader(name = VOICE_TURN_ID_HEADER, required = false) UUID requestedTurnId,
            @RequestBody byte[] wavAudio
    ) {
        DeviceTokenService.DeviceToken deviceToken = authenticator.authenticate(request);
        UUID turnId = requestedTurnId == null ? UUID.randomUUID() : requestedTurnId;
        validateAudio(deviceToken.deviceId(), turnId, wavAudio);
        StreamingResponseBody body = output -> {
            VoiceTurnStreamEnvelope.Writer writer = streamEnvelope.writer(output);
            try {
                voiceTurnService.handleStreaming(deviceToken.deviceId(), turnId, wavAudio, writer);
            } catch (VoiceTurnClientDisconnectedException ignored) {
                return;
            } catch (RuntimeException exception) {
                try {
                    writer.error(streamErrorCode(exception));
                } catch (VoiceTurnClientDisconnectedException ignored) {
                    // The device already closed this turn; a late error frame must be discarded.
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(VOICE_TURN_STREAM_MEDIA_TYPE)
                .header(VOICE_TURN_ID_HEADER, turnId.toString())
                .body(body);
    }

    @PostMapping(
            path = "/voice/turn",
            consumes = {"audio/wav", MediaType.APPLICATION_OCTET_STREAM_VALUE},
            produces = "application/vnd.stackchan.voice-turn"
    )
    public ResponseEntity<byte[]> voiceTurn(
            HttpServletRequest request,
            @RequestHeader(name = VOICE_TURN_ID_HEADER, required = false) UUID requestedTurnId,
            @RequestBody byte[] wavAudio
    ) {
        DeviceTokenService.DeviceToken deviceToken = authenticator.authenticate(request);
        UUID turnId = requestedTurnId == null ? UUID.randomUUID() : requestedTurnId;
        validateAudio(deviceToken.deviceId(), turnId, wavAudio);
        byte[] body = envelope.encode(voiceTurnService.handle(deviceToken.deviceId(), turnId, wavAudio));
        return ResponseEntity.ok()
                .contentType(VOICE_TURN_MEDIA_TYPE)
                .header(VOICE_TURN_ID_HEADER, turnId.toString())
                .contentLength(body.length)
                .body(body);
    }

    private void validateAudio(UUID deviceId, UUID turnId, byte[] wavAudio) {
        if (wavAudio.length >= MIN_WAV_BYTES && wavAudio.length <= MAX_WAV_BYTES) return;
        diagnosticsService.recordServerStage(
                deviceId, turnId, VoiceTurnStage.FAILED, VoiceTurnFailureCode.NO_SPEECH
        );
        throw new VoiceInputException("语音数据大小无效");
    }

    private String streamErrorCode(RuntimeException exception) {
        if (exception instanceof VoiceInputException) return "no_speech";
        if (exception instanceof VoiceTurnCancelledException) return "cancelled";
        if (exception instanceof LlmProviderUnavailableException) return "llm_unavailable";
        if (exception instanceof SpeechProviderUnavailableException) return "speech_unavailable";
        return "internal_error";
    }
}
