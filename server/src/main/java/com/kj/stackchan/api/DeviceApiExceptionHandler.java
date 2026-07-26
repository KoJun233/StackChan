package com.kj.stackchan.api;

import java.nio.charset.StandardCharsets;

import com.kj.stackchan.conversation.ConversationNotFoundException;
import com.kj.stackchan.device.InvalidDeviceRefreshCredentialException;
import com.kj.stackchan.device.InvalidDeviceTokenException;
import com.kj.stackchan.llm.InvalidLlmSettingsException;
import com.kj.stackchan.llm.LlmProviderUnavailableException;
import com.kj.stackchan.memory.InvalidMemoryException;
import com.kj.stackchan.memory.MemoryNotFoundException;
import com.kj.stackchan.persona.InvalidPersonaException;
import com.kj.stackchan.speech.InvalidSpeechSettingsException;
import com.kj.stackchan.speech.SpeechProviderUnavailableException;
import com.kj.stackchan.speech.VoiceInputException;
import com.kj.stackchan.speech.VoiceTurnCancelledException;
import com.kj.stackchan.reminder.ReminderNotFoundException;
import com.kj.stackchan.reminder.InvalidReminderException;
import com.kj.stackchan.wakeword.InvalidWakeWordModelJobException;
import com.kj.stackchan.wakeword.WakeWordModelCatalogUnavailableException;
import com.kj.stackchan.wakeword.WakeWordModelNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class DeviceApiExceptionHandler {

    public static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
    public static final ApiError PAIRING_CODE_UNAVAILABLE = new ApiError(
            "pairing_code_unavailable", "配对码无效、已使用或已过期。"
    );
    public static final ApiError DEVICE_OFFLINE = new ApiError(
            "device_offline", "设备当前离线，无法接收安全停止命令。"
    );
    public static final ApiError CONVERSATION_NOT_FOUND = new ApiError(
            "conversation_not_found", "未找到指定对话。"
    );
    public static final ApiError INVALID_LLM_SETTINGS = new ApiError(
            "invalid_llm_settings", "AI 配置不完整或无效。"
    );
    public static final ApiError LLM_PROVIDER_UNAVAILABLE = new ApiError(
            "llm_provider_unavailable", "模型服务暂时不可用，请稍后重试。"
    );
    public static final ApiError INVALID_SPEECH_SETTINGS = new ApiError(
            "invalid_speech_settings", "语音配置不完整或无效。"
    );
    public static final ApiError SPEECH_PROVIDER_UNAVAILABLE = new ApiError(
            "speech_provider_unavailable", SpeechProviderUnavailableException.SAFE_MESSAGE
    );
    public static final ApiError AUTHENTICATION_FAILED = new ApiError(
            "authentication_failed", "用户名或密码不正确。"
    );
    public static final ApiError DEVICE_CREDENTIALS_INVALID = new ApiError(
            "device_credentials_invalid", "设备凭据无效，请通过 USB 重新配对。"
    );
    public static final ApiError INVALID_REQUEST = new ApiError(
            "invalid_request", "请求参数无效。"
    );
    public static final ApiError VOICE_INPUT_INVALID = new ApiError(
            "voice_input_invalid", "没有识别到有效语音，请重试。"
    );
    public static final ApiError VOICE_TURN_CANCELLED = new ApiError(
            "voice_turn_cancelled", "语音对话已取消。"
    );
    public static final ApiError REMINDER_NOT_FOUND = new ApiError(
            "reminder_not_found", "未找到指定提醒。"
    );
    public static final ApiError INVALID_REMINDER = new ApiError(
            "invalid_reminder", "提醒内容、时间、时区或目标设备无效。"
    );
    public static final ApiError MEMORY_NOT_FOUND = new ApiError(
            "memory_not_found", "未找到指定记忆。"
    );
    public static final ApiError INVALID_MEMORY = new ApiError(
            "invalid_memory", "记忆内容、状态或作用范围无效。"
    );
    public static final ApiError INVALID_PERSONA = new ApiError(
            "invalid_persona", "人设内容或选项无效。"
    );
    public static final ApiError INVALID_WAKE_WORD_MODEL_JOB = new ApiError(
            "invalid_wake_word_model_job", "乐鑫内置唤醒模型、目标设备无效，或该设备已有进行中的模型任务。"
    );
    public static final ApiError WAKE_WORD_MODEL_NOT_FOUND = new ApiError(
            "wake_word_model_not_found", "未找到可供当前设备下载的唤醒模型。"
    );
    public static final ApiError WAKE_WORD_MODEL_CATALOG_UNAVAILABLE = new ApiError(
            "wake_word_model_catalog_unavailable", "乐鑫内置唤醒模型暂时不可用，请稍后重试。"
    );

    @ExceptionHandler(PairingCodeUnavailableException.class)
    ResponseEntity<ApiError> pairingCodeUnavailable(PairingCodeUnavailableException exception) {
        return response(HttpStatus.CONFLICT, PAIRING_CODE_UNAVAILABLE);
    }

    @ExceptionHandler(DeviceOfflineException.class)
    ResponseEntity<ApiError> deviceOffline(DeviceOfflineException exception) {
        return response(HttpStatus.CONFLICT, DEVICE_OFFLINE);
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    ResponseEntity<ApiError> conversationNotFound(ConversationNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, CONVERSATION_NOT_FOUND);
    }

    @ExceptionHandler(InvalidLlmSettingsException.class)
    ResponseEntity<ApiError> invalidLlmSettings(InvalidLlmSettingsException exception) {
        return response(HttpStatus.BAD_REQUEST, INVALID_LLM_SETTINGS);
    }

    @ExceptionHandler(LlmProviderUnavailableException.class)
    ResponseEntity<ApiError> llmProviderUnavailable(LlmProviderUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, LLM_PROVIDER_UNAVAILABLE);
    }

    @ExceptionHandler(InvalidSpeechSettingsException.class)
    ResponseEntity<ApiError> invalidSpeechSettings(InvalidSpeechSettingsException exception) {
        return response(HttpStatus.BAD_REQUEST, INVALID_SPEECH_SETTINGS);
    }

    @ExceptionHandler(SpeechProviderUnavailableException.class)
    ResponseEntity<ApiError> speechProviderUnavailable(SpeechProviderUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, SPEECH_PROVIDER_UNAVAILABLE);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authenticationFailure(AuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, AUTHENTICATION_FAILED);
    }

    @ExceptionHandler(InvalidDeviceRefreshCredentialException.class)
    ResponseEntity<ApiError> invalidDeviceRefreshCredential(InvalidDeviceRefreshCredentialException exception) {
        return response(HttpStatus.UNAUTHORIZED, DEVICE_CREDENTIALS_INVALID);
    }

    @ExceptionHandler(InvalidDeviceTokenException.class)
    ResponseEntity<ApiError> invalidDeviceToken(InvalidDeviceTokenException exception) {
        return response(HttpStatus.UNAUTHORIZED, DEVICE_CREDENTIALS_INVALID);
    }

    @ExceptionHandler(VoiceInputException.class)
    ResponseEntity<ApiError> invalidVoiceInput(VoiceInputException exception) {
        return response(HttpStatus.BAD_REQUEST, VOICE_INPUT_INVALID);
    }

    @ExceptionHandler(VoiceTurnCancelledException.class)
    ResponseEntity<ApiError> voiceTurnCancelled(VoiceTurnCancelledException exception) {
        return response(HttpStatus.CONFLICT, VOICE_TURN_CANCELLED);
    }

    @ExceptionHandler(ReminderNotFoundException.class)
    ResponseEntity<ApiError> reminderNotFound(ReminderNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, REMINDER_NOT_FOUND);
    }

    @ExceptionHandler(InvalidReminderException.class)
    ResponseEntity<ApiError> invalidReminder(InvalidReminderException exception) {
        return response(HttpStatus.BAD_REQUEST, INVALID_REMINDER);
    }

    @ExceptionHandler(MemoryNotFoundException.class)
    ResponseEntity<ApiError> memoryNotFound(MemoryNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, MEMORY_NOT_FOUND);
    }

    @ExceptionHandler(InvalidMemoryException.class)
    ResponseEntity<ApiError> invalidMemory(InvalidMemoryException exception) {
        return response(HttpStatus.BAD_REQUEST, INVALID_MEMORY);
    }

    @ExceptionHandler(InvalidPersonaException.class)
    ResponseEntity<ApiError> invalidPersona(InvalidPersonaException exception) {
        return response(HttpStatus.BAD_REQUEST, INVALID_PERSONA);
    }

    @ExceptionHandler(InvalidWakeWordModelJobException.class)
    ResponseEntity<ApiError> invalidWakeWordModelJob(InvalidWakeWordModelJobException exception) {
        return response(HttpStatus.CONFLICT, INVALID_WAKE_WORD_MODEL_JOB);
    }

    @ExceptionHandler(WakeWordModelCatalogUnavailableException.class)
    ResponseEntity<ApiError> wakeWordModelCatalogUnavailable(WakeWordModelCatalogUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, WAKE_WORD_MODEL_CATALOG_UNAVAILABLE);
    }

    @ExceptionHandler(WakeWordModelNotFoundException.class)
    ResponseEntity<ApiError> wakeWordModelNotFound(WakeWordModelNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, WAKE_WORD_MODEL_NOT_FOUND);
    }



    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> invalidRequest(Exception exception, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if ("/api/v1/devices/token:refresh".equals(requestUri)) {
            return response(HttpStatus.UNAUTHORIZED, DEVICE_CREDENTIALS_INVALID);
        }
        if (requestUri.startsWith("/api/v1/settings/llm")) {
            return response(HttpStatus.BAD_REQUEST, INVALID_LLM_SETTINGS);
        }
        if (requestUri.startsWith("/api/v1/settings/speech")) {
            return response(HttpStatus.BAD_REQUEST, INVALID_SPEECH_SETTINGS);
        }
        if (requestUri.startsWith("/api/v1/persona")) {
            return response(HttpStatus.BAD_REQUEST, INVALID_PERSONA);
        }
        if (requestUri.startsWith("/api/v1/memories")) {
            return response(HttpStatus.BAD_REQUEST, INVALID_MEMORY);
        }
        if (requestUri.startsWith("/api/v1/auth/")) {
            return response(HttpStatus.UNAUTHORIZED, AUTHENTICATION_FAILED);
        }
        if ("/api/v1/pairing/claim".equals(requestUri) && isBlankPairingCode(exception)) {
            return response(HttpStatus.CONFLICT, PAIRING_CODE_UNAVAILABLE);
        }
        return response(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
    }

    private boolean isBlankPairingCode(Exception exception) {
        if (!(exception instanceof MethodArgumentNotValidException validationException)) {
            return false;
        }
        return validationException.getBindingResult()
                .getFieldErrors("pairingCode")
                .stream()
                .anyMatch(fieldError -> "NotBlank".equals(fieldError.getCode()));
    }

    private ResponseEntity<ApiError> response(HttpStatus status, ApiError body) {
        return ResponseEntity.status(status)
                .contentType(JSON_UTF8)
                .body(body);
    }

    public record ApiError(String code, String message) {
    }
}
