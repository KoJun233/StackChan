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
import com.kj.stackchan.role.CompanionRoleService;
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
    private static final Pattern UNSOURCED_CURRENT_EVENT = Pattern.compile(
            "(?:最近|近期|今天|刚刚|刚才).{0,24}(?:发布|推出|更新|上线|发生|消息|新闻|技术|模型|版本|比赛|价格)"
                    + "|(?:最新消息|最新新闻|最新技术|最新模型|最新版本|新发布|新推出)"
    );
    private static final String SYSTEM_RULES = """
            你只为已经由应用规则批准的主动问候生成措辞，不能决定何时发送。
            按提供的当前角色人设自然开场；有已确认兴趣时，可以从该兴趣或紧邻话题切入，
            但不能把相邻话题写成用户已经喜欢的事实。
            当前没有提供外部资讯来源，不得声称“最近、今天、刚刚、新发布、新技术”等实时事实，
            也不得补充训练知识中的新闻、版本、比赛结果、价格或日期。
            输出一句简体中文纯文本，2到100个字符。
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
        return generate(fallbackContent, memory, null);
    }

    public GenerationResult generate(
            String fallbackContent,
            LongTermMemoryService.MemorySnapshot memory,
            CompanionRoleService.RoleSnapshot role
    ) {
        if (memory == null && role == null) {
            return new GenerationResult(fallbackContent, ProactiveGenerationStatus.FIXED);
        }
        CompletableFuture<String> future = null;
        try {
            future = CompletableFuture.supplyAsync(() -> clientFactory.createChatClient()
                    .prompt()
                    .system(SYSTEM_RULES)
                    .user(generationContext(memory, role))
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

    private String generationContext(
            LongTermMemoryService.MemorySnapshot memory,
            CompanionRoleService.RoleSnapshot role
    ) {
        StringBuilder context = new StringBuilder();
        if (role != null) {
            context.append("当前角色名：").append(role.name())
                    .append("\n交流语气：").append(role.tone())
                    .append("\n回复长度：").append(role.replyLength())
                    .append("\n主动程度：").append(role.proactivity())
                    .append("\n角色背景：").append(role.backgroundInstructions())
                    .append("\n话题边界：").append(role.topicBoundaries())
                    .append("\n禁忌：").append(role.taboos());
        }
        if (memory != null) {
            context.append("\n已确认兴趣或事实标题：").append(memory.title())
                    .append("\n已确认兴趣或事实：").append(memory.content())
                    .append("\n稳定主题：").append(memory.topicKey());
        } else {
            context.append("\n当前没有可主动提及的已确认兴趣，不要虚构用户喜好。请只按人设自然打个招呼。");
        }
        return context.toString();
    }

    String validate(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 100
                || normalized.contains("\n") || normalized.contains("\r")
                || normalized.matches("^[\"'“”‘’].*|.*[\"'“”‘’]$")
                || FORBIDDEN.matcher(normalized.toLowerCase(Locale.ROOT)).find()
                || UNSOURCED_CURRENT_EVENT.matcher(normalized).find()) {
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
