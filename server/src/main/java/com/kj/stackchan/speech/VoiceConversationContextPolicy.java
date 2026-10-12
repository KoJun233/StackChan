package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
                .filter(message -> VoiceDialogueControl.parse(message.content()).closesTopic()
                        || VoiceDialogueControl.parse(message.content()).kind() == VoiceDialogueControl.Kind.NEW_TOPIC
                        || VoiceDialogueControl.parse(message.content()).kind() == VoiceDialogueControl.Kind.CORRECTION)
                .filter(message -> message.createdAt() != null)
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
                .filter(message -> message.content() != null && !message.content().isBlank())
                .filter(message -> message.createdAt() != null && !message.createdAt().isAfter(now))
                .filter(message -> boundary == null || !message.createdAt().isBefore(boundary))
                .collect(Collectors.toMap(ConversationMessageSnapshot::id, Function.identity(), (first, duplicate) -> first));
        // A duplicated completion cannot create two apparent turns or a unique continuation object.
        var completionCounts = history.stream()
                .filter(message -> message.role() == MessageRole.ASSISTANT)
                .filter(message -> message.generationStatus() == GenerationStatus.COMPLETED)
                .filter(message -> message.inReplyToMessageId() != null)
                .collect(Collectors.groupingBy(ConversationMessageSnapshot::inReplyToMessageId, Collectors.counting()));
        var assistants = history.stream()
                .filter(message -> message.role() == MessageRole.ASSISTANT)
                .filter(message -> message.generationStatus() == GenerationStatus.COMPLETED)
                .filter(message -> message.content() != null && !message.content().isBlank())
                .filter(message -> message.inReplyToMessageId() != null
                        && users.containsKey(message.inReplyToMessageId()))
                .filter(message -> completionCounts.get(message.inReplyToMessageId()) == 1L)
                .filter(message -> message.completedAt() != null
                        && !message.completedAt().isBefore(cutoff)
                        && !message.completedAt().isAfter(now)
                        && !message.completedAt().isBefore(users.get(message.inReplyToMessageId()).createdAt()))
                .sorted(Comparator.comparing(ConversationMessageSnapshot::completedAt)
                        .thenComparing(ConversationMessageSnapshot::id))
                .toList();
        return assistants.stream()
                .skip(Math.max(0, assistants.size() - MAX_RECENT_TURNS))
                .flatMap(assistant -> java.util.stream.Stream.of(
                        users.get(assistant.inReplyToMessageId()), assistant))
                .toList();
    }

    /** Keep only the current object and its continuation chain; ambiguous latest objects require clarification. */
    public List<ConversationMessageSnapshot> selectContinuation(List<ConversationMessageSnapshot> history,
                                                               Instant topicBoundary) {
        List<ConversationMessageSnapshot> selected = select(history, topicBoundary);
        if (selected.isEmpty()) return List.of();
        Instant latestCompletion = selected.getLast().completedAt();
        if (selected.stream().filter(message -> message.role() == MessageRole.ASSISTANT
                && latestCompletion.equals(message.completedAt())).count() != 1) return List.of();
        List<ConversationMessageSnapshot> chain = new ArrayList<>();
        for (int index = selected.size() - 2; index >= 0; index -= 2) {
            var user = selected.get(index);
            var assistant = selected.get(index + 1);
            var dialogue = VoiceDialogueControl.parse(user.content());
            if (assistant.content().equals("你想继续哪件事？")
                    || assistant.content().equals("你想让我继续讲哪个话题？")
                    || assistant.content().equals("这一段可能没说完，请再说一小段。")
                    || (dialogue.kind() == VoiceDialogueControl.Kind.NEW_TOPIC && dialogue.routingText().isBlank()))
                return List.of();
            chain.addFirst(assistant);
            chain.addFirst(user);
            if (dialogue.kind() != VoiceDialogueControl.Kind.CONTINUE) return List.copyOf(chain);
        }
        return List.of();
    }
}
