package com.kj.stackchan.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationContentBufferTest {

    @Test
    void appendsAndSnapshotsSafelyAcrossConcurrentThreads() throws Exception {
        int writerCount = 8;
        int appendsPerWriter = 500;
        int tokenLength = 6;
        GenerationContentBuffer buffer = new GenerationContentBuffer();
        ExecutorService executor = Executors.newFixedThreadPool(writerCount + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch writersDone = new CountDownLatch(writerCount);
        List<Future<?>> writers = new ArrayList<>();
        Set<String> expectedTokens = new HashSet<>();
        for (int writer = 0; writer < writerCount; writer++) {
            int writerIndex = writer;
            for (int append = 0; append < appendsPerWriter; append++) {
                expectedTokens.add("%02d%04d".formatted(writerIndex, append));
            }
            writers.add(executor.submit(() -> {
                start.await();
                snapshotReady.await();
                try {
                    for (int append = 0; append < appendsPerWriter; append++) {
                        buffer.append("%02d%04d".formatted(writerIndex, append));
                    }
                } finally {
                    writersDone.countDown();
                }
                return null;
            }));
        }
        Future<Integer> snapshots = executor.submit(() -> {
            start.await();
            snapshotReady.countDown();
            int count = 0;
            while (writersDone.getCount() > 0) {
                assertThat(buffer.snapshot().length() % tokenLength).isZero();
                count++;
            }
            return count;
        });

        start.countDown();
        for (Future<?> writer : writers) {
            writer.get(10, TimeUnit.SECONDS);
        }
        assertThat(snapshots.get(10, TimeUnit.SECONDS)).isPositive();
        executor.shutdownNow();

        String finalContent = buffer.snapshot();
        assertThat(finalContent).hasSize(writerCount * appendsPerWriter * tokenLength);
        Set<String> actualTokens = new HashSet<>();
        for (int offset = 0; offset < finalContent.length(); offset += tokenLength) {
            actualTokens.add(finalContent.substring(offset, offset + tokenLength));
        }
        assertThat(actualTokens).containsExactlyInAnyOrderElementsOf(expectedTokens);
    }
}
