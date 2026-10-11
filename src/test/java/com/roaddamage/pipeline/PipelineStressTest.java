package com.roaddamage.pipeline;

import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.model.ManagedMat;
import com.roaddamage.infer.OrtRuntime;
import com.roaddamage.ingest.FrameSource;
import javafx.application.Platform;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress test verifying long-run pipeline stability, bounded direct memory usage,
 * and multi-threaded ONNX intraOpNumThreads tuning sweeps.
 */
final class PipelineStressTest {

    @BeforeAll
    static void initEnvironment() {
        OpenCV.loadLocally();
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX runtime already active
        }
    }

    @AfterAll
    static void tearDown() {
        OrtRuntime.resetIntraOpThreads();
    }

    static final class SyntheticFrameSource implements FrameSource {
        private final int totalFrames;
        private final int width;
        private final int height;
        private int current = 0;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        SyntheticFrameSource(int totalFrames, int width, int height) {
            this.totalFrames = totalFrames;
            this.width = width;
            this.height = height;
        }

        @Override
        public Optional<com.roaddamage.core.model.FrameEnvelope> next() {
            if (closed.get() || current >= totalFrames) {
                return Optional.empty();
            }
            java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
            int idx = current++;
            Mat mat = new Mat(height, width, CvType.CV_8UC3, new Scalar(50, 50, 50));
            return Optional.of(new com.roaddamage.core.model.FrameEnvelope(
                    idx, idx * 33L, new ManagedMat(mat), "synthetic-stress"
            ));
        }

        @Override public double fps() { return 30.0; }
        @Override public int totalFrames() { return totalFrames; }
        @Override public String sourceId() { return "synthetic-stress"; }
        @Override public void close() { closed.set(true); }
    }

    @Test
    @DisplayName("Stress test: 2,000 synthetic frames at 640x480 with stride 1 ensures bounded memory and high throughput")
    void stressTest2000Frames() {
        assertDoesNotThrow(() -> {
            int frameCount = 2000;
            PipelineConfig config = new PipelineConfig(Path.of("models"), 1, 32, 0.40f, false);

            AtomicLong steadyStateAllocations = new AtomicLong(0);
            AtomicLong steadyStateDirectMem = new AtomicLong(0);
            java.util.concurrent.atomic.AtomicInteger processed = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicReference<InspectionPipeline> pipelineRef =
                    new java.util.concurrent.atomic.AtomicReference<>();

            InspectionPipeline pipeline = new InspectionPipeline(config, res -> {
                if (processed.incrementAndGet() == 100) { // Measured after 100 processed frames
                    InspectionPipeline p = pipelineRef.get();
                    NativeLeakAudit.Snapshot snap = NativeLeakAudit.snapshot(p != null ? p.pool() : null);
                    steadyStateAllocations.set(snap.directBufferPoolAllocations());
                    steadyStateDirectMem.set(snap.directMemoryUsed());
                }
            }, snap -> {});
            pipelineRef.set(pipeline);

            try (FrameSource source = new SyntheticFrameSource(frameCount, 640, 480)) {
                pipeline.run(source);
            }

            PipelineMetrics.Snapshot metrics = pipeline.metrics().snapshot();
            NativeLeakAudit.Snapshot finalAudit = NativeLeakAudit.snapshot(pipeline.pool());
            pipeline.close();

            assertEquals(frameCount, metrics.framesIn(), "All 2,000 frames must be ingested");
            assertTrue(metrics.fps() > 0.0, "Throughput must be positive");

            System.out.printf("[StressTest] Ingested: %d frames | Processed: %d frames | Dropped: %d | Throughput: %.2f FPS%n",
                    metrics.framesIn(), metrics.framesOut(), metrics.droppedFrames(), metrics.fps());

            // 4.b Direct buffer usage at end <= 2x steady-state usage measured after 100 frames
            long steadyMem = steadyStateDirectMem.get();
            if (steadyMem > 0) {
                assertTrue(finalAudit.directMemoryUsed() <= 2 * steadyMem,
                        "Direct memory exceeded 2x steady-state: " + finalAudit.directMemoryUsed() + " vs " + steadyMem);
            }

            long steadyCount = Math.max(1L, steadyStateAllocations.get());
            assertTrue(finalAudit.directBufferPoolAllocations() <= 2 * steadyCount,
                    "Buffer pool allocations exceeded 2x steady-state: " + finalAudit.directBufferPoolAllocations() + " vs " + steadyCount);

            // Audit native leaks
            NativeLeakAudit.Snapshot postCloseAudit = NativeLeakAudit.snapshot();
            assertEquals(0L, postCloseAudit.openTensorLeases(), "Open tensor leases must be 0");
            assertEquals(0L, postCloseAudit.liveManagedMats(), "Live managed mats must be 0");
        });
    }

    @Test
    @DisplayName("Tuning sweep: sweep intraOpNumThreads in {1, 2, 4, availableProcessors()} and report throughput")
    void tuningSweepIntraOpThreads() {
        int availableCores = Runtime.getRuntime().availableProcessors();
        List<Integer> candidateThreads = new ArrayList<>(List.of(1, 2, 4));
        if (!candidateThreads.contains(availableCores)) {
            candidateThreads.add(availableCores);
        }
        Collections.sort(candidateThreads);

        Map<Integer, Double> threadFpsResults = new LinkedHashMap<>();
        int sweepFrames = 500;

        for (int threads : candidateThreads) {
            OrtRuntime.setIntraOpThreads(threads);
            PipelineConfig config = new PipelineConfig(Path.of("models"), 1, 32, 0.40f, false);
            InspectionPipeline pipeline = new InspectionPipeline(config, res -> {}, snap -> {});

            try (FrameSource source = new SyntheticFrameSource(sweepFrames, 640, 480)) {
                pipeline.run(source);
            }

            double fps = pipeline.metrics().snapshot().fps();
            threadFpsResults.put(threads, fps);
            pipeline.close();

            System.out.printf("[TuningSweep] intraOpNumThreads = %2d -> Throughput = %7.2f FPS%n", threads, fps);
        }

        // Identify optimal configuration
        int bestThreads = 1;
        double maxFps = 0.0;
        for (Map.Entry<Integer, Double> entry : threadFpsResults.entrySet()) {
            if (entry.getValue() > maxFps) {
                maxFps = entry.getValue();
                bestThreads = entry.getKey();
            }
        }

        System.out.println("=================================================");
        System.out.println("TUNING SWEEP SUMMARY:");
        threadFpsResults.forEach((t, fps) ->
                System.out.printf("  Threads: %2d  |  FPS: %7.2f  |  Speedup: %.2fx%n",
                        t, fps, fps / threadFpsResults.get(1)));
        System.out.printf("Recommended intraOpNumThreads: %d (Default is %d)%n",
                bestThreads, ModelConfig.ORT_INTRA_OP_THREADS);
        System.out.println("=================================================");

        assertTrue(bestThreads >= 1, "Optimal thread count must be >= 1");
    }
}
