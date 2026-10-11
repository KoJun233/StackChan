package com.kj.stackchan.delegation;

import java.time.Instant;
import java.util.List;

public interface ResearchBriefGenerator {
    GeneratedBrief generate(ResearchRequest request);

    record ResearchRequest(String title, String instruction, List<String> sourceUrls,
            Instant from, Instant to, int maxItems, List<String> excludedUrls) {}

    record GeneratedBrief(List<BriefItem> items, List<String> warnings) {}

    record BriefItem(String title, String summary, String impact, String sourceName,
            String url, Instant publishedAt) {}
}
