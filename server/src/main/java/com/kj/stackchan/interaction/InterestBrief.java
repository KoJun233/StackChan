package com.kj.stackchan.interaction;

import java.time.Instant;

public record InterestBrief(
        String sourceName,
        String title,
        String url,
        Instant publishedAt,
        Instant retrievedAt
) {
}
