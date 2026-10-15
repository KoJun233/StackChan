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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"换个话题，聊音乐", "我问的是天气", "不是咖啡而是茶"})
    void explicitNewObjectCreatesABoundaryEvenWithoutTheStoredBoundary(String current) {
        var oldUser = message(MessageRole.USER, "聊咖啡", GenerationStatus.COMPLETED, NOW.minusSeconds(80));
        var oldReply = message(MessageRole.ASSISTANT, "咖啡适合慢慢品。", GenerationStatus.COMPLETED, NOW.minusSeconds(70));
        var user = message(MessageRole.USER, current, GenerationStatus.COMPLETED, NOW.minusSeconds(40));
        var reply = message(MessageRole.ASSISTANT, "本轮新对象的回答。", GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        assertThat(policy.select(List.of(oldUser, oldReply, user, reply))).containsExactly(user, reply);
    }

    @Test
    void continuationUsesOnlyTheLatestObjectAndItsCompletedContinuationChain() {
        var oldUser = message(MessageRole.USER, "咖啡有什么特点", GenerationStatus.COMPLETED, NOW.minusSeconds(100));
        var oldReply = message(MessageRole.ASSISTANT, "咖啡有香气。", GenerationStatus.COMPLETED, NOW.minusSeconds(90));
        var currentUser = message(MessageRole.USER, "说说月亮", GenerationStatus.COMPLETED, NOW.minusSeconds(50));
        var currentReply = message(MessageRole.ASSISTANT, "月亮绕地球运行。", GenerationStatus.COMPLETED, NOW.minusSeconds(40));
        var nextUser = message(MessageRole.USER, "继续讲", GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        var nextReply = message(MessageRole.ASSISTANT, "月亮自身不会发光。", GenerationStatus.COMPLETED, NOW.minusSeconds(20));
        assertThat(policy.selectContinuation(List.of(oldUser, oldReply, currentUser, currentReply, nextUser, nextReply), null))
                .containsExactly(currentUser, currentReply, nextUser, nextReply);
    }

    @Test
    void equallyRecentObjectsCannotBePickedByRandomUuidOrder() {
        var firstUser = message(MessageRole.USER, "咖啡", GenerationStatus.COMPLETED, NOW.minusSeconds(50));
        var firstReply = message(MessageRole.ASSISTANT, "咖啡话题。", GenerationStatus.COMPLETED, NOW.minusSeconds(20));
        var secondUser = message(MessageRole.USER, "音乐", GenerationStatus.COMPLETED, NOW.minusSeconds(40));
        var secondReply = message(MessageRole.ASSISTANT, "音乐话题。", GenerationStatus.COMPLETED, NOW.minusSeconds(20));
        assertThat(policy.selectContinuation(List.of(firstUser, firstReply, secondUser, secondReply), null)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "换个话题|想聊点什么？", "继续讲|你想继续哪件事？",
            "继续讲|你想让我继续讲哪个话题？", "添加任务|这一段可能没说完，请再说一小段。"
    })
    void clarificationAndIncompleteCaptureCannotSupplyAContinuationObject(String text, String answer) {
        var user = message(MessageRole.USER, text, GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        var reply = message(MessageRole.ASSISTANT, answer, GenerationStatus.COMPLETED, NOW.minusSeconds(20));
        assertThat(policy.selectContinuation(List.of(user, reply), null)).isEmpty();
    }

    @Test
    void duplicateCompletedAssistantsDoNotCountAsTwoSuccessfulTurns() {
        var user = message(MessageRole.USER, "说说咖啡", GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        var reply = message(MessageRole.ASSISTANT, "咖啡话题。", GenerationStatus.COMPLETED, NOW.minusSeconds(20));
        var duplicate = new ConversationMessageSnapshot(UUID.randomUUID(), MessageRole.ASSISTANT,
                "重复回答", GenerationStatus.COMPLETED, NOW.minusSeconds(25), NOW.minusSeconds(10), user.id());
        assertThat(policy.select(List.of(user, reply, duplicate))).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = GenerationStatus.class, names = {"FAILED", "INTERRUPTED", "STREAMING"})
    void unsuccessfulAssistantsCannotProvideFactsOrContinuation(GenerationStatus status) {
        var user = message(MessageRole.USER, "说说天气", GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        var reply = message(MessageRole.ASSISTANT, "残留天气回答", status, NOW.minusSeconds(20));
        assertThat(policy.select(List.of(user, reply))).isEmpty();
        assertThat(policy.selectContinuation(List.of(user, reply), null)).isEmpty();
    }

    @Test
    void futureAndBlankCompletionsDoNotMasqueradeAsRecentFacts() {
        var firstUser = message(MessageRole.USER, "未来问题", GenerationStatus.COMPLETED, NOW.plusSeconds(2));
        var futureReply = message(MessageRole.ASSISTANT, "未来回答", GenerationStatus.COMPLETED, NOW.plusSeconds(3));
        var secondUser = message(MessageRole.USER, "空回答的问题", GenerationStatus.COMPLETED, NOW.minusSeconds(40));
        var blankReply = message(MessageRole.ASSISTANT, " ", GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        assertThat(policy.select(List.of(firstUser, futureReply, secondUser, blankReply))).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"结束聊天。", "不聊这个"})
    void endingClosesOlderHistoryAndTheClosingTurnIsNotAContinuationTopic(String transcript) {
        var oldUser = message(MessageRole.USER, "说说咖啡", GenerationStatus.COMPLETED, NOW.minusSeconds(80));
        var oldReply = message(MessageRole.ASSISTANT, "咖啡话题", GenerationStatus.COMPLETED, NOW.minusSeconds(70));
        var end = message(MessageRole.USER, transcript, GenerationStatus.COMPLETED, NOW.minusSeconds(60));
        var goodbye = message(MessageRole.ASSISTANT, "好的，先聊到这里。", GenerationStatus.COMPLETED, NOW.minusSeconds(50));
        assertThat(policy.select(List.of(oldUser, oldReply, end, goodbye))).isEmpty();
        var nextUser = message(MessageRole.USER, "聊聊音乐", GenerationStatus.COMPLETED, NOW.minusSeconds(40));
        var nextReply = message(MessageRole.ASSISTANT, "你想聊哪类音乐？", GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        assertThat(policy.select(List.of(oldUser, oldReply, end, goodbye, nextUser, nextReply), end.createdAt()))
                .containsExactly(nextUser, nextReply);
    }

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

    @Test
    void topicBoundaryExcludesOldUsersEvenWhenTheirAssistantFinishesLater() {
        Instant boundary = NOW.minusSeconds(60);
        var oldUser = message(MessageRole.USER, "旧天气", GenerationStatus.COMPLETED, NOW.minusSeconds(90));
        var lateAssistant = message(MessageRole.ASSISTANT, "迟到的旧回答", GenerationStatus.COMPLETED, NOW.minusSeconds(40));
        var newUser = message(MessageRole.USER, "换个话题", GenerationStatus.COMPLETED, NOW.minusSeconds(30));
        var newAssistant = message(MessageRole.ASSISTANT, "想聊什么？", GenerationStatus.COMPLETED, NOW.minusSeconds(20));
        assertThat(policy.select(List.of(oldUser, lateAssistant, newUser, newAssistant), boundary))
                .containsExactly(newUser, newAssistant);
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
