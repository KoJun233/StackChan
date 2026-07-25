package com.kj.stackchan.wakeword;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WakeWordModelJobSchedulerTest {

    @Test
    void dispatchesReadyBuiltInModels() {
        WakeWordModelJobService jobService = mock(WakeWordModelJobService.class);
        WakeWordModelJobScheduler scheduler = new WakeWordModelJobScheduler(jobService);

        scheduler.dispatch();

        verify(jobService).dispatchReadyJobs();
    }
}
