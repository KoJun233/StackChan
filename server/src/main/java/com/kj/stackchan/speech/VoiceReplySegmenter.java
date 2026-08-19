package com.kj.stackchan.speech;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class VoiceReplySegmenter {

    static final int TARGET_SEGMENT_CHARACTERS = 80;
    static final int HARD_SPLIT_CHARACTERS = 160;
    static final int MAX_SEGMENTS = 8;

    public List<String> segment(String reply) {
        if (reply == null || reply.isBlank()) return List.of();
        String normalized = reply.trim();
        List<String> segments = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < normalized.length() && segments.size() < MAX_SEGMENTS - 1; index++) {
            int length = index - start + 1;
            char current = normalized.charAt(index);
            boolean strongBoundary = isStrongBoundary(current);
            boolean softBoundary = isSoftBoundary(current) && length >= TARGET_SEGMENT_CHARACTERS / 2;
            boolean hardBoundary = length >= HARD_SPLIT_CHARACTERS;
            if (strongBoundary || softBoundary || hardBoundary) {
                add(segments, normalized.substring(start, index + 1));
                start = index + 1;
            }
        }
        add(segments, normalized.substring(start));
        return List.copyOf(segments);
    }

    private void add(List<String> segments, String text) {
        String value = text.trim();
        if (!value.isEmpty()) segments.add(value);
    }

    private boolean isStrongBoundary(char value) {
        return value == '。' || value == '！' || value == '？' || value == '!'
                || value == '?' || value == '\n';
    }

    private boolean isSoftBoundary(char value) {
        return value == '；' || value == ';' || value == '，' || value == ',';
    }
}
