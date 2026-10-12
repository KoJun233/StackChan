package com.kj.stackchan.delegation;

/** Only a fixed code is retained; provider bodies, URLs and credentials never become errors. */
public final class ResearchException extends RuntimeException {
    private final String code;

    public ResearchException(String code) {
        super(safeCode(code));
        this.code = safeCode(code);
    }

    public String code() { return code; }

    private static String safeCode(String code) {
        return switch (code == null ? "" : code) {
            case "SOURCES_UNAVAILABLE", "RESEARCH_TIMEOUT", "MODEL_UNAVAILABLE", "MODEL_OUTPUT_INVALID",
                    "INVALID_RESEARCH_REQUEST" -> code;
            default -> "RESEARCH_FAILED";
        };
    }
}
