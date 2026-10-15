package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.speech.VoiceQualityService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class CompanionVoiceQualityController {
    private final VoiceQualityService service;
    public CompanionVoiceQualityController(VoiceQualityService service) { this.service = service; }

    @GetMapping("/api/v1/companion/voice-quality")
    public ResponseEntity<VoiceQualityService.Summary> quality(@RequestParam UUID deviceId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary(deviceId, from, to));
    }

    @ExceptionHandler(VoiceQualityService.QualityRequestException.class)
    ResponseEntity<DeviceApiExceptionHandler.ApiError> rejected(VoiceQualityService.QualityRequestException exception) {
        return ResponseEntity.status(exception.notFound() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST)
                .cacheControl(CacheControl.noStore()).body(new DeviceApiExceptionHandler.ApiError(
                        exception.notFound() ? "RESOURCE_NOT_FOUND" : "INVALID_REQUEST",
                        exception.notFound() ? "未找到可访问的设备。" : "请选择有效设备和不超过七天的时间范围。"));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<DeviceApiExceptionHandler.ApiError> invalid(Exception exception) {
        return rejected(new VoiceQualityService.QualityRequestException(false));
    }
}
