package com.kj.stackchan.api;

import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.speech.VoiceInputException;
import com.kj.stackchan.speech.VoiceTurnEnvelope;
import com.kj.stackchan.speech.VoiceTurnService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceVoiceController {

    public static final MediaType VOICE_TURN_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.stackchan.voice-turn"
    );
    private static final int MIN_WAV_BYTES = 44;
    private static final int MAX_WAV_BYTES = 512 * 1024;

    private final DeviceHttpAuthenticator authenticator;
    private final VoiceTurnService voiceTurnService;
    private final VoiceTurnEnvelope envelope;

    public DeviceVoiceController(
            DeviceHttpAuthenticator authenticator,
            VoiceTurnService voiceTurnService,
            VoiceTurnEnvelope envelope
    ) {
        this.authenticator = authenticator;
        this.voiceTurnService = voiceTurnService;
        this.envelope = envelope;
    }

    @PostMapping(
            path = "/voice/turn",
            consumes = {"audio/wav", MediaType.APPLICATION_OCTET_STREAM_VALUE},
            produces = "application/vnd.stackchan.voice-turn"
    )
    public ResponseEntity<byte[]> voiceTurn(HttpServletRequest request, @RequestBody byte[] wavAudio) {
        if (wavAudio.length < MIN_WAV_BYTES || wavAudio.length > MAX_WAV_BYTES) {
            throw new VoiceInputException("语音数据大小无效");
        }
        DeviceTokenService.DeviceToken deviceToken = authenticator.authenticate(request);
        byte[] body = envelope.encode(voiceTurnService.handle(deviceToken.deviceId(), wavAudio));
        return ResponseEntity.ok()
                .contentType(VOICE_TURN_MEDIA_TYPE)
                .contentLength(body.length)
                .body(body);
    }
}
