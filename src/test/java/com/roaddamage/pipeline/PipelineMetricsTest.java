package com.roaddamage.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipelineMetricsTest {

    @Test
    @DisplayName("Basic counter and latency tracking works as expected")
    void basicCounterAndLatencyTracking() {
        PipelineMetrics metrics = new PipelineMetrics();

        metrics.recordFrameIn();
        metrics.recordFrameIn();
        metrics.recordFrameOut();
        metrics.recordSkippedStage2();
        metrics.recordError();
        metrics.recordDroppedFrame();

        metrics.recordStageDurations(10_000_000L, 20_000_000L, 15_000_000L);
        metrics.recordStageDurations(5_000_000L, 25_000_000L, 10_000_000L);

        PipelineMetrics.Snapshot snap = metrics.snapshot();
        assertNotNull(snap);
        assertEquals(2L, snap.framesIn());
        assertEquals(1L, snap.framesOut());
        assertEquals(1L, snap.framesSkippedStage2());
        assertEquals(1L, snap.errors());
        assertEquals(1L, snap.droppedFrames());

        assertEquals(10_000_000L, metrics.maxStage1Nanos());
        assertEquals(25_000_000L, metrics.maxStage2Nanos());
        assertEquals(15_000_000L, metrics.maxStage3Nanos());
        assertEquals(45_000_000L, metrics.maxTotalNanos());
        assertEquals(45_000_000L, snap.maxStageNanos());

        assertTrue(snap.elapsedNanos() >= 0L);
        assertTrue(snap.fps() >= 0.0);
    }

    @Test
    @DisplayName("Concurrent updates are thread-safe and lossless")
    void concurrentUpdatesThreadSafe() throws InterruptedException {
        PipelineMetrics metrics = new PipelineMetrics();
        int threads = 8;
        int iterationsPerThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        metrics.recordFrameIn();
                        metrics.recordFrameOut();
                        metrics.recordDroppedFrame();
                        metrics.recordError();
                        metrics.recordSkippedStage2();
                        metrics.recordStageDurations(100L, 200L, 300L);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        PipelineMetrics.Snapshot snap = metrics.snapshot();
        long expected = (long) threads * iterationsPerThread;
        assertEquals(expected, snap.framesIn());
        assertEquals(expected, snap.framesOut());
        assertEquals(expected, snap.droppedFrames());
        assertEquals(expected, snap.errors());
        assertEquals(expected, snap.framesSkippedStage2());
        assertEquals(100L, metrics.maxStage1Nanos());
        assertEquals(200L, metrics.maxStage2Nanos());
        assertEquals(300L, metrics.maxStage3Nanos());
        assertEquals(600L, metrics.maxTotalNanos());
        assertEquals(600L, snap.maxStageNanos());
    }

    @Test
    @DisplayName("Snapshot computes valid fps and formats toString cleanly")
    void snapshotDerivedFields() {
        PipelineMetrics metrics = new PipelineMetrics();
        PipelineMetrics.Snapshot snap = metrics.snapshot();
        assertEquals(0.0, snap.fps(), 1e-6);

        String str = snap.toString();
        assertNotNull(str);
        assertTrue(str.contains("framesIn=0"));
    }
}
