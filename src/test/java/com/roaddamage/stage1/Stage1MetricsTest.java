package com.roaddamage.stage1;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage1MetricsTest {

    @Test
    void emptyMetricsSnapshotReturnsZeroes() {
        Stage1Metrics metrics = new Stage1Metrics();
        Stage1Metrics.Snapshot s = metrics.snapshot();
        assertEquals(0L, s.count());
        assertEquals(0L, s.damagedCount());
        assertEquals(0L, s.totalNanos());
        assertEquals(0L, s.minNanos());
        assertEquals(0L, s.maxNanos());
        assertEquals(0.0, s.averageNanos());
        assertEquals(0.0, s.damageRate());
    }

    @Test
    void recordsResultsAndCalculatesAggregates() {
        Stage1Metrics metrics = new Stage1Metrics();
        metrics.record(new MobileNetV3Screener.Stage1Result(0.8f, 0.2f, true, 10_000L));
        metrics.record(new MobileNetV3Screener.Stage1Result(0.1f, 0.9f, false, 20_000L));
        metrics.record(new MobileNetV3Screener.Stage1Result(0.9f, 0.1f, true, 30_000L));

        Stage1Metrics.Snapshot s = metrics.snapshot();
        assertEquals(3L, s.count());
        assertEquals(2L, s.damagedCount());
        assertEquals(60_000L, s.totalNanos());
        assertEquals(10_000L, s.minNanos());
        assertEquals(30_000L, s.maxNanos());
        assertEquals(20_000.0, s.averageNanos());
        assertEquals(2.0 / 3.0, s.damageRate(), 1e-6);

        metrics.reset();
        assertEquals(0L, metrics.snapshot().count());
    }

    @Test
    void concurrentRecordingMaintainsIntegrity() throws InterruptedException {
        Stage1Metrics metrics = new Stage1Metrics();
        int threads = 4;
        int perThread = 250;
        CountDownLatch latch = new CountDownLatch(threads);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            metrics.record(i % 2 == 0, 1000L + i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        Stage1Metrics.Snapshot s = metrics.snapshot();
        assertEquals((long) threads * perThread, s.count());
        assertEquals((long) threads * (perThread / 2), s.damagedCount());
        assertTrue(s.minNanos() >= 1000L);
    }
}
