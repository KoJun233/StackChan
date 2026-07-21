package com.kj.stackchan.conversation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamingGenerationRecoveryTest {

    @Mock
    private ConversationService service;

    @InjectMocks
    private StreamingGenerationRecovery recovery;

    @Test
    void recoversStreamingRowsAtApplicationStartup() throws Exception {
        when(service.recoverStreamingGenerations()).thenReturn(2);

        recovery.run(new DefaultApplicationArguments(new String[0]));

        verify(service).recoverStreamingGenerations();
    }
}
