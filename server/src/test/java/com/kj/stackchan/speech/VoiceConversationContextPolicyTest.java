package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.GenerationStatus;
import com.kj.stackchan.conversation.MessageRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceConversationContextPolicyTest {
    private UUID pendingUserId;

    private static final Instant NOW = Instant.parse("2026-09-11T14:00:00Z");
    private final VoiceConversationContextPolicy policy = new VoiceConversationContextPolicy(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void keepsOnlyTheLatestFourCompleteRecentTurns() {
        List<ConversationMessageSnapshot> history = new ArrayList<>();
        for (int turn = 1; turn <= 6; turn++) {
            Instant createdAt = NOW.minusSeconds((7L - turn) * 60);
            history.add(message(MessageRole.USER, "用户" + turn, GenerationStatus.COMPLETED, createdAt));
            history.add(message(MessageRole.ASSISTANT, "回复" + turn, GenerationStatus.COMPLETED,
                    createdAt.plusSeconds(10)));
        }

        assertThat(policy.select(history))
                .extracting(ConversationMessageSnapshot::content)
                .containsExactly("用户3", "回复3", "用户4", "回复4", "用户5", "回复5", "用户6", "回复6");
    }

    @Test
    void dropsStaleTurnsAndIncompleteOrOrphanedMessages() {
        List<ConversationMessageSnapshot> history = List.of(
                message(MessageRole.USER, "旧问题", GenerationStatus.COMPLETED, NOW.minusSeconds(1_900)),
                message(MessageRole.ASSISTANT, "旧回答", GenerationStatus.COMPLETED, NOW.minusSeconds(1_850)),
                message(MessageRole.ASSISTANT, "孤立回答", GenerationStatus.COMPLETED, NOW.minusSeconds(300)),
                message(MessageRole.USER, "失败问题", GenerationStatus.COMPLETED, NOW.minusSeconds(240)),
                message(MessageRole.ASSISTANT, "失败回答", GenerationStatus.FAILED, NOW.minusSeconds(230)),
                message(MessageRole.USER, "刚才又输了", GenerationStatus.COMPLETED, NOW.minusSeconds(120)),
                message(MessageRole.ASSISTANT, "这把确实可惜。", GenerationStatus.COMPLETED, NOW.minusSeconds(110)),
                message(MessageRole.USER, "没有回答的问题", GenerationStatus.COMPLETED, NOW.minusSeconds(10))
        );

        assertThat(policy.select(history))
                .extracting(ConversationMessageSnapshot::content)
                .containsExactly("刚才又输了", "这把确实可惜。");
    }

    @Test
    void includesATurnWhoseAssistantCompletedExactlyAtTheCutoff() {
        Instant cutoff = NOW.minus(VoiceConversationContextPolicy.RECENT_CONTEXT_AGE);
        List<ConversationMessageSnapshot> history = List.of(
                message(MessageRole.USER, "刚才那个", GenerationStatus.COMPLETED, cutoff.minusSeconds(15)),
                message(MessageRole.ASSISTANT, "你说的是设置页面。", GenerationStatus.COMPLETED, cutoff)
        );

        assertThat(policy.select(history)).hasSize(2);
    }

    private ConversationMessageSnapshot message(
            MessageRole role,
            String content,
            GenerationStatus status,
            Instant completedAt
    ) {
        UUID id = UUID.randomUUID();
        UUID replyTo = role == MessageRole.ASSISTANT ? pendingUserId : null;
        pendingUserId = role == MessageRole.USER ? id : null;
        return new ConversationMessageSnapshot(
                id, role, content, status, completedAt.minusSeconds(1), completedAt, replyTo);
    }

    @Test
    void matchesByReplyIdDespiteTimestampOrderingAndDeletedUser() {
        var user = message(MessageRole.USER, "这把赢了", GenerationStatus.COMPLETED, NOW.minusSeconds(20));
        var assistant = message(MessageRole.ASSISTANT, "漂亮", GenerationStatus.COMPLETED, NOW.minusSeconds(10));
        var otherUser = message(MessageRole.USER, "失败的提问", GenerationStatus.COMPLETED, NOW.minusSeconds(9));
        var orphan = new ConversationMessageSnapshot(UUID.randomUUID(), MessageRole.ASSISTANT,
                "已删除问题的回答", GenerationStatus.COMPLETED, NOW.minusSeconds(8), NOW.minusSeconds(7), UUID.randomUUID());
        assertThat(policy.select(List.of(assistant, user, otherUser, orphan))).containsExactly(user, assistant);
    }
}
