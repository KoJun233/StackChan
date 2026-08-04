package com.kj.stackchan.memory;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MemorySuggestionExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(MemorySuggestionExtractionService.class);
    private static final String EXTRACTION_RULES = """
            你只负责判断当前完成回合是否包含值得跨会话保存的、用户明确表达的稳定偏好或重要事件。
            不得推断，不得保存密码、验证码、令牌、密钥、精确地址、身份号码、银行卡号、财务状况或医疗状况。
            没有高价值建议时只返回 {"suggest":false}。
            有建议时只返回一个 JSON 对象：
            {"suggest":true,"category":"USER_PROFILE|EVENT","title":"不超过120字","content":"不超过2000字的可核对事实","topicKey":"不超过120字的稳定主题键","importance":1到5,"reason":"不超过500字的建议原因"}
            不要返回 Markdown、解释、第二条建议或其他字段。
            """;

    private final Executor executor;
    private final LlmRuntimeClientFactory llmRuntimeClientFactory;
    private final LongTermMemoryService memoryService;
    private final ObjectMapper objectMapper;

    public MemorySuggestionExtractionService(
            @Qualifier("memorySuggestionExecutor") Executor executor,
            LlmRuntimeClientFactory llmRuntimeClientFactory,
            LongTermMemoryService memoryService,
            ObjectMapper objectMapper
    ) {
        this.executor = executor;
        this.llmRuntimeClientFactory = llmRuntimeClientFactory;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    public void schedule(SuggestionTurn turn) {
        if (turn == null || turn.sourceTurnId() == null || turn.userText() == null || turn.assistantText() == null) {
            return;
        }
        try {
            executor.execute(() -> extract(turn));
        } catch (RejectedExecutionException exception) {
            logger.warn("Memory suggestion queue unavailable for turn={}", turn.sourceTurnId());
        }
    }

    void extract(SuggestionTurn turn) {
        try {
            String result = llmRuntimeClientFactory.createChatClient()
                    .prompt()
                    .system(EXTRACTION_RULES)
                    .user("用户：" + turn.userText() + "\n机器人：" + turn.assistantText())
                    .call()
                    .content();
            SuggestionDraft draft = parse(result);
            if (draft == null) {
                return;
            }
            MemoryScopeType scopeType = turn.deviceId() == null ? MemoryScopeType.GLOBAL : MemoryScopeType.DEVICE;
            memoryService.suggest(new LongTermMemoryService.MemorySuggestionCommand(
                    new LongTermMemoryService.MemoryCommand(
                            scopeType,
                            turn.deviceId(),
                            draft.category(),
                            draft.title(),
                            draft.content(),
                            draft.topicKey(),
                            draft.importance(),
                            false
                    ),
                    draft.reason(),
                    turn.sourceTurnId()
            ));
        } catch (RuntimeException exception) {
            logger.warn("Memory suggestion extraction failed for turn={}", turn.sourceTurnId());
        }
    }

    private SuggestionDraft parse(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        String json = stripFence(result.trim());
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject() || !root.path("suggest").asBoolean(false)) {
                return null;
            }
            if (root.size() > 7) {
                return null;
            }
            return new SuggestionDraft(
                    MemoryCategory.valueOf(requiredText(root, "category")),
                    requiredText(root, "title"),
                    requiredText(root, "content"),
                    requiredText(root, "topicKey"),
                    root.path("importance").intValue(),
                    requiredText(root, "reason")
            );
        } catch (RuntimeException | java.io.IOException exception) {
            return null;
        }
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Invalid memory suggestion field");
        }
        return value.textValue();
    }

    private String stripFence(String value) {
        if (!value.startsWith("```") || !value.endsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0) {
            return value;
        }
        return value.substring(firstLineEnd + 1, value.length() - 3).trim();
    }

    public record SuggestionTurn(UUID sourceTurnId, UUID deviceId, String userText, String assistantText) {
    }

    private record SuggestionDraft(
            MemoryCategory category,
            String title,
            String content,
            String topicKey,
            int importance,
            String reason
    ) {
    }
}
