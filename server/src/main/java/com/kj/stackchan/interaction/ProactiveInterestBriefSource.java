package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProactiveInterestBriefSource {

    private static final Logger logger = LoggerFactory.getLogger(ProactiveInterestBriefSource.class);
    private static final Duration CACHE_MAX_AGE = Duration.ofHours(2);

    private final HackerNewsBriefClient client;
    private final Clock clock;
    private volatile Cache cache = new Cache(List.of(), null);

    @Autowired
    public ProactiveInterestBriefSource(HackerNewsBriefClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT20S")
    public void refresh() {
        Instant now = clock.instant();
        try {
            List<InterestBrief> candidates = client.fetch(now);
            if (!candidates.isEmpty()) {
                cache = new Cache(candidates, now);
                logger.info("Interest brief refresh completed: source=hacker_news candidates={}", candidates.size());
            } else {
                logger.warn("Interest brief refresh unavailable at stage=no_usable_candidates");
            }
        } catch (RuntimeException exception) {
            logger.warn("Interest brief refresh unavailable at stage=source_fetch");
        }
    }

    public List<InterestBrief> current() {
        Cache snapshot = cache;
        if (snapshot.refreshedAt() == null
                || snapshot.refreshedAt().isBefore(clock.instant().minus(CACHE_MAX_AGE))) return List.of();
        return snapshot.candidates();
    }

    private record Cache(List<InterestBrief> candidates, Instant refreshedAt) {
    }
}
