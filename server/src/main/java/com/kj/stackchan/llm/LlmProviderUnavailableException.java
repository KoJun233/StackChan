package com.kj.stackchan.llm;

public class LlmProviderUnavailableException extends RuntimeException {

    public static final String SAFE_MESSAGE = "模型服务暂时不可用，请检查 AI 配置";

    public LlmProviderUnavailableException() {
        super(SAFE_MESSAGE);
    }
}
