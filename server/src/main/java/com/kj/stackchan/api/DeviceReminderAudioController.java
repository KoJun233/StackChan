package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.reminder.ReminderDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/device/reminders")
public class DeviceReminderAudioController {

    private static final MediaType AUDIO_WAV = new MediaType("audio", "wav");

    private final DeviceHttpAuthenticator authenticator;
    private final ReminderDeliveryService reminderDeliveryService;

    public DeviceReminderAudioController(
            DeviceHttpAuthenticator authenticator,
            ReminderDeliveryService reminderDeliveryService
    ) {
        this.authenticator = authenticator;
        this.reminderDeliveryService = reminderDeliveryService;
    }

    @GetMapping(path = "/{reminderId}/audio", produces = "audio/wav")
    public ResponseEntity<byte[]> audio(HttpServletRequest request, @PathVariable UUID reminderId) {
        DeviceTokenService.DeviceToken deviceToken = authenticator.authenticate(request);
        byte[] audio = reminderDeliveryService.getAudio(reminderId, deviceToken.deviceId());
        return ResponseEntity.ok().contentType(AUDIO_WAV).contentLength(audio.length).body(audio);
    }
}
