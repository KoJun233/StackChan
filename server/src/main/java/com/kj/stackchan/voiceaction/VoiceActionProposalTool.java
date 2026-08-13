package com.kj.stackchan.voiceaction;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;

public class VoiceActionProposalTool {
    public static final String ID = "submit_voice_action_proposal";
    private final UUID deviceId;
    private final UUID conversationId;
    private final UUID turnId;
    private final VoiceActionProposalService proposalService;
    private final ObjectMapper objectMapper;
    private VoiceActionProposalService.ProposalSnapshot submitted;

    public VoiceActionProposalTool(UUID deviceId, UUID conversationId, UUID turnId,
                                   VoiceActionProposalService proposalService, ObjectMapper objectMapper) {
        this.deviceId = deviceId; this.conversationId = conversationId; this.turnId = turnId;
        this.proposalService = proposalService; this.objectMapper = objectMapper;
    }

    @Tool(name = ID, description = "提交一个经过应用复核的语音动作提案；只保存提案，不执行业务动作。")
    public String submit(Input input) {
        VoiceActionDraft draft;
        try {
            VoiceActionType type = VoiceActionType.valueOf(input.actionType());
            draft = new VoiceActionDraft(type, type != VoiceActionType.CREATE_MEMORY_SUGGESTION,
                    input.content(), input.title(), parseInstant(input.scheduledAt()), input.zoneId(),
                    input.recurrenceType(), input.recurrenceInterval(), input.durationMinutes(),
                    parseInstant(input.targetAt()), input.volumePercent(), input.memoryCategory(), null);
        } catch (RuntimeException exception) {
            throw new VoiceActionException("Voice action tool input is invalid");
        }
        submitted = proposalService.submit(deviceId, conversationId, turnId, draft);
        try {
            return objectMapper.writeValueAsString(new Result(
                    submitted.id(), submitted.actionType(), submitted.status(), submitted.expiresAt().toString()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize voice action proposal", exception);
        }
    }

    public VoiceActionProposalService.ProposalSnapshot submittedProposal() { return submitted; }

    private Instant parseInstant(String value) { return value == null || value.isBlank() ? null : Instant.parse(value); }

    public record Input(String actionType, String content, String title, String scheduledAt, String zoneId,
                        String recurrenceType, Integer recurrenceInterval, Integer durationMinutes,
                        String targetAt, Integer volumePercent, String memoryCategory) { }
    private record Result(UUID proposalId, VoiceActionType actionType, VoiceActionStatus status, String expiresAt) { }
}
