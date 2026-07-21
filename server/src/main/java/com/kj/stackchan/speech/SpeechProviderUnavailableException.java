package com.kj.stackchan.speech;

public class SpeechProviderUnavailableException extends RuntimeException {

    public static final String SAFE_MESSAGE = "语音服务暂时不可用，请检查语音配置。";
    private static final String DEFAULT_DIAGNOSTIC_CODE = "speech_provider_unavailable";

    private final String diagnosticCode;

    public SpeechProviderUnavailableException() {
        this(DEFAULT_DIAGNOSTIC_CODE, null);
    }

    public SpeechProviderUnavailableException(Throwable cause) {
        this(DEFAULT_DIAGNOSTIC_CODE, cause);
    }

    SpeechProviderUnavailableException(String diagnosticCode) {
        this(diagnosticCode, null);
    }

    SpeechProviderUnavailableException(String diagnosticCode, Throwable cause) {
        super(SAFE_MESSAGE, cause);
        this.diagnosticCode = safeDiagnosticCode(diagnosticCode);
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }

    static String httpDiagnosticCode(String stage, int statusCode) {
        int safeStatus = statusCode >= 100 && statusCode <= 599 ? statusCode : 0;
        return safeDiagnosticCode(stage) + "_http_" + safeStatus;
    }

    private static String safeDiagnosticCode(String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,80}")) {
            return DEFAULT_DIAGNOSTIC_CODE;
        }
        return value;
    }
}
