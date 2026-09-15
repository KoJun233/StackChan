package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.GenerationStatus;
import com.kj.stackchan.conversation.MessageRole;
import org.springframework.stereotype.Component;

@Component
public class VoiceConversationContextPolicy {

    static final Duration RECENT_CONTEXT_AGE = Duration.ofMinutes(30);
    static final int MAX_RECENT_TURNS = 4;

    private final Clock clock;

    public VoiceConversationContextPolicy(Clock clock) {
        this.clock = clock;
    }

    public List<ConversationMessageSnapshot> select(List<ConversationMessageSnapshot> history) {
        return select(history, null);
    }

    public Instant topicBoundary(List<ConversationMessageSnapshot> history, Instant storedBoundary) {
        Instant latestEnd = history.stream()
                .filter(message -> message.role() == MessageRole.USER)
                .filter(message -> VoiceDialogueControl.parse(message.content()).closesTopic())
                .map(ConversationMessageSnapshot::createdAt).max(Instant::compareTo).orElse(null);
        return latestEnd != null && (storedBoundary == null || latestEnd.isAfter(storedBoundary))
                ? latestEnd : storedBoundary;
    }

    public List<ConversationMessageSnapshot> select(List<ConversationMessageSnapshot> history, Instant topicBoundary) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(RECENT_CONTEXT_AGE);
        Instant boundary = topicBoundary(history, topicBoundary);
        var users = history.stream()
                .filter(message -> message.role() == MessageRole.USER)
                .filter(message -> !VoiceDialogueControl.parse(message.content()).closesTopic())
                .filter(message -> message.generationStatus() == GenerationStatus.COMPLETED)
                .filter(message -> boundary == null || !message.createdAt().isBefore(boundary))
                .collect(Collectors.toMap(ConversationMessageSnapshot::id, Function.identity()));
        var assistants = history.stream()
                .filter(message -> message.role() == MessageRole.ASSISTANT)
                .filter(message -> message.generationStatus() == GenerationStatus.COMPLETED)
                .filter(message -> message.inReplyToMessageId() != null
                        && users.containsKey(message.inReplyToMessageId()))
                .filter(message -> message.completedAt() != null
                        && !message.completedAt().isBefore(cutoff)
                        && !message.completedAt().isAfter(now))
                .sorted(Comparator.comparing(ConversationMessageSnapshot::completedAt)
                        .thenComparing(ConversationMessageSnapshot::id))
                .toList();
        return assistants.stream()
                .skip(Math.max(0, assistants.size() - MAX_RECENT_TURNS))
                .flatMap(assistant -> java.util.stream.Stream.of(
                        users.get(assistant.inReplyToMessageId()), assistant))
                .toList();
    }
}
