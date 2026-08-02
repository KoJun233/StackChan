package com.kj.stackchan.memory;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class CompletedTurnMemoryCoordinator {

    private final LongTermMemoryService memoryService;
    private final MemorySuggestionExtractionService suggestionExtractionService;

    public CompletedTurnMemoryCoordinator(
            LongTermMemoryService memoryService,
            MemorySuggestionExtractionService suggestionExtractionService
    ) {
        this.memoryService = memoryService;
        this.suggestionExtractionService = suggestionExtractionService;
    }

    public void complete(
            UUID usageTurnId,
            UUID sourceTurnId,
            UUID deviceId,
            String userText,
            String assistantText,
            List<UUID> usedMemoryIds,
            boolean extractSuggestion
    ) {
        memoryService.recordUsage(usageTurnId, usedMemoryIds);
        if (extractSuggestion) {
            suggestionExtractionService.schedule(new MemorySuggestionExtractionService.SuggestionTurn(
                    sourceTurnId, deviceId, userText, assistantText
            ));
        }
    }
}
