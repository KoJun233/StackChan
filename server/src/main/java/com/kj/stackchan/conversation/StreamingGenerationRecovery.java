package com.kj.stackchan.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StreamingGenerationRecovery implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(StreamingGenerationRecovery.class);

    private final ConversationService service;

    public StreamingGenerationRecovery(ConversationService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        int recoveredCount = service.recoverStreamingGenerations();
        logger.info("Recovered {} streaming chat generations at startup", recoveredCount);
    }
}
