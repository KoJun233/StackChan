package com.kj.stackchan.interaction;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.reminder.ProactiveGenerationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ProactiveMessageGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ProactiveMessageGenerator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(https?://|www\\.|```|[#*\\[\\]_~<>]|抑郁症|焦虑症|精神疾病|诊断|治疗|服药|药物|病情|"
                    + "情绪低落|心情.{0,4}(?:不好|低落)|你.{0,6}(?:焦虑|抑郁|难过)|系统提示|忽略.{0,6}指令)"
    );
    private static final String SYSTEM_RULES = """
            你只为已经由应用规则批准的主动问候生成措辞，不能决定何时发送。
            只依据提供的一条已确认事实，输出一句简体中文纯文本，2到100个字符。
            语气温和但不过度亲密，不做情绪、医疗或人格诊断，不给治疗建议，不编造新事实。
            不输出 Markdown、URL、引号、换行、解释、标签或第二句话。
            """;

    private final Executor executor;
    private final LlmRuntimeClientFactory clientFactory;

    public ProactiveMessageGenerator(
            @Qualifier("proactiveGenerationExecutor") Executor executor,
            LlmRuntimeClientFactory clientFactory
    ) {
        this.executor = executor;
        this.clientFactory = clientFactory;
    }

    public GenerationResult generate(
            String fallbackContent,
            LongTermMemoryService.MemorySnapshot memory
    ) {
        if (memory == null) {
            return new GenerationResult(fallbackContent, ProactiveGenerationStatus.FIXED);
        }
        CompletableFuture<String> future = null;
        try {
            future = CompletableFuture.supplyAsync(() -> clientFactory.createChatClient()
                    .prompt()
                    .system(SYSTEM_RULES)
                    .user("已确认事实标题：" + memory.title() + "\n已确认事实：" + memory.content())
                    .call()
                    .content(), executor);
            String content = validate(future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            if (content == null) {
                logger.warn("Proactive wording rejected at stage=output_policy");
                return new GenerationResult(fallbackContent, ProactiveGenerationStatus.FALLBACK);
            }
            return new GenerationResult(content, ProactiveGenerationStatus.GENERATED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (future != null) future.cancel(true);
            logger.warn("Proactive wording unavailable at stage=interrupted");
            return new GenerationResult(fallbackContent, ProactiveGenerationStatus.FALLBACK);
        } catch (Exception exception) {
            if (future != null) future.cancel(true);
            logger.warn("Proactive wording unavailable at stage=generation");
            return new GenerationResult(fallbackContent, ProactiveGenerationStatus.FALLBACK);
        }
    }

    String validate(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 100
                || normalized.contains("\n") || normalized.contains("\r")
                || normalized.matches("^[\"'“”‘’].*|.*[\"'“”‘’]$")
                || FORBIDDEN.matcher(normalized.toLowerCase(Locale.ROOT)).find()) {
            return null;
        }
        int sentenceMarks = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?') {
                sentenceMarks++;
                if (i != normalized.length() - 1) return null;
            }
        }
        return sentenceMarks <= 1 ? normalized : null;
    }

    public record GenerationResult(String content, ProactiveGenerationStatus status) {
    }
}
