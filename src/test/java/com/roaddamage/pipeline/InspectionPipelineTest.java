package com.roaddamage.pipeline;

import com.roaddamage.core.error.InferenceException;
import com.roaddamage.core.error.RoadDamageException;
import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import com.roaddamage.core.model.FrameEnvelope;
import com.roaddamage.core.model.ManagedMat;
import com.roaddamage.core.model.SeverityLevel;
import com.roaddamage.infer.TensorLease;
import com.roaddamage.ingest.FrameSource;
import com.roaddamage.stage1.MobileNetV3Screener;
import com.roaddamage.stage2.YoloV8Detector;
import com.roaddamage.ui.FrameSnapshot;
import javafx.application.Platform;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InspectionPipelineTest {

    private static final Path MOCK_DIR = Path.of("models");

    @BeforeAll
    static void initEnvironment() {
        OpenCV.loadLocally();
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX already started
        }
    }

    private static final class MockFrameSource implements FrameSource {
        private final int total;
        private int current = 0;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        MockFrameSource(int total) {
            this.total = total;
        }

        @Override
        public Optional<FrameEnvelope> next() {
            if (closed.get() || current >= total) {
                return Optional.empty();
            }
            int idx = current++;
            Mat mat = new Mat(64, 64, CvType.CV_8UC3, new Scalar(100, 150, 200));
            return Optional.of(new FrameEnvelope(idx, idx * 33L, new ManagedMat(mat), "mock-source"));
        }

        @Override public double fps() { return 30.0; }
        @Override public int totalFrames() { return total; }
        @Override public String sourceId() { return "mock-source"; }
        @Override public void close() { closed.set(true); }
    }

    @Test
    @DisplayName("End-to-end processing across stages emits FrameResult and FrameSnapshot")
    void pipelineProcessesFramesEndToEnd() throws Exception {
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 1, 16, 0.40f, true);
        List<FrameResult> results = new CopyOnWriteArrayList<>();
        List<FrameSnapshot> snapshots = new CopyOnWriteArrayList<>();

        InspectionPipeline.Stage1Function s1 = mat ->
                new MobileNetV3Screener.Stage1Result(0.85f, 0.15f, true, 1_000_000L);

        Detection sampleDetection = new Detection(new BoundingBox(10, 10, 50, 50), DamageClass.D40_POTHOLE, 0.92f);
        InspectionPipeline.Stage2Function s2 = mat ->
                new YoloV8Detector.Stage2DetectionResult(List.of(sampleDetection), 1_000_000L, 1_500_000L);

        DamageAssessment assessment = new DamageAssessment(
                sampleDetection, 0.1f, 0.2f, 0.7f, 0.15f, 1.0f, 85, SeverityLevel.CRITICAL, "Severe pothole"
        );
        InspectionPipeline.Stage3Function s3 = (mat, detections) -> List.of(assessment);

        try (InspectionPipeline pipeline = new InspectionPipeline(config, results::add, snapshots::add, s1, s2, s3);
             FrameSource source = new MockFrameSource(5)) {
            pipeline.run(source);

            assertEquals(5, results.size());
            assertEquals(5, snapshots.size());

            for (FrameResult r : results) {
                assertTrue(r.damaged());
                assertEquals(1, r.assessments().size());
                assertEquals(85, r.assessments().get(0).priorityScore());
            }

            PipelineMetrics.Snapshot metrics = pipeline.metrics().snapshot();
            assertEquals(5, metrics.framesIn());
            assertEquals(5, metrics.framesOut());
            assertEquals(0, metrics.framesSkippedStage2());
            assertEquals(0, metrics.errors());
            assertEquals(0, metrics.droppedFrames());
            assertTrue(metrics.fps() > 0.0);
        }
    }

    @Test
    @DisplayName("Clean frames skip Stage 2 & Stage 3 when skipStage2WhenClean is true")
    void skipsStage2WhenCleanIfConfigured() throws Exception {
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 1, 16, 0.40f, true);
        List<FrameResult> results = new CopyOnWriteArrayList<>();
        List<FrameSnapshot> snapshots = new CopyOnWriteArrayList<>();

        AtomicInteger s2Invocations = new AtomicInteger(0);
        AtomicInteger s3Invocations = new AtomicInteger(0);

        InspectionPipeline.Stage1Function s1 = mat ->
                new MobileNetV3Screener.Stage1Result(0.10f, 0.90f, false, 800_000L);
        InspectionPipeline.Stage2Function s2 = mat -> {
            s2Invocations.incrementAndGet();
            return new YoloV8Detector.Stage2DetectionResult(List.of(), 500_000L, 500_000L);
        };
        InspectionPipeline.Stage3Function s3 = (mat, detections) -> {
            s3Invocations.incrementAndGet();
            return List.of();
        };

        try (InspectionPipeline pipeline = new InspectionPipeline(config, results::add, snapshots::add, s1, s2, s3);
             FrameSource source = new MockFrameSource(4)) {
            pipeline.run(source);

            assertEquals(4, results.size());
            assertEquals(4, snapshots.size());
            assertEquals(0, s2Invocations.get(), "Stage 2 should not be invoked when clean");
            assertEquals(0, s3Invocations.get(), "Stage 3 should not be invoked when clean");

            for (FrameResult r : results) {
                assertFalse(r.damaged());
                assertTrue(r.assessments().isEmpty());
            }

            PipelineMetrics.Snapshot metrics = pipeline.metrics().snapshot();
            assertEquals(4, metrics.framesSkippedStage2());
            assertEquals(0, metrics.framesOut());
            assertEquals(metrics.framesOut(), metrics.framesIn() - metrics.framesSkippedStage2() - metrics.droppedFrames());
            assertEquals(0, metrics.errors());
        }
    }

    @Test
    @DisplayName("Frame stride subsamples incoming frames accurately")
    void frameStrideSubsamplesFrames() throws Exception {
        // frameStride = 3, processes frameIndex 0, 3, 6, 9 (4 out of 10)
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 3, 16, 0.40f, false);
        List<FrameResult> results = new CopyOnWriteArrayList<>();

        InspectionPipeline.Stage1Function s1 = mat ->
                new MobileNetV3Screener.Stage1Result(0.20f, 0.80f, false, 500_000L);
        InspectionPipeline.Stage2Function s2 = mat ->
                new YoloV8Detector.Stage2DetectionResult(List.of(), 500_000L, 500_000L);
        InspectionPipeline.Stage3Function s3 = (mat, detections) -> List.of();

        try (InspectionPipeline pipeline = new InspectionPipeline(config, results::add, snap -> {}, s1, s2, s3);
             FrameSource source = new MockFrameSource(10)) {
            pipeline.run(source);

            assertEquals(4, results.size());
            assertEquals(0L, results.get(0).frameIndex());
            assertEquals(3L, results.get(1).frameIndex());
            assertEquals(6L, results.get(2).frameIndex());
            assertEquals(9L, results.get(3).frameIndex());

            PipelineMetrics.Snapshot metrics = pipeline.metrics().snapshot();
            assertEquals(4, metrics.framesIn());
            assertEquals(4, metrics.framesOut());
        }
    }

    @Test
    @DisplayName("Bounded queue drops frames under back-pressure without blocking indefinitely")
    void queueDropsFramesUnderBackpressure() throws Exception {
        // Capacity = 1, artificially stall stage 1
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 1, 1, 0.40f, true);
        List<FrameResult> results = new CopyOnWriteArrayList<>();

        CountDownLatch firstFrameStarted = new CountDownLatch(1);
        CountDownLatch allowInfer = new CountDownLatch(1);

        InspectionPipeline.Stage1Function s1 = mat -> {
            firstFrameStarted.countDown();
            try {
                allowInfer.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            return new MobileNetV3Screener.Stage1Result(0.20f, 0.80f, false, 500_000L);
        };

        try (InspectionPipeline pipeline = new InspectionPipeline(config, results::add, snap -> {}, s1,
                mat -> new YoloV8Detector.Stage2DetectionResult(List.of(), 0L, 0L),
                (mat, d) -> List.of())) {

            // Source generating 30 frames rapidly
            Thread runner = new Thread(() -> {
                try (FrameSource source = new MockFrameSource(30)) {
                    pipeline.run(source);
                } catch (Exception ignored) {}
            });
            runner.start();

            // Wait until infer thread takes the first frame
            assertTrue(firstFrameStarted.await(2, TimeUnit.SECONDS));
            // Let ingest fill the queue and drop excess frames
            Thread.sleep(150);
            allowInfer.countDown();
            runner.join(5000);

            PipelineMetrics.Snapshot metrics = pipeline.metrics().snapshot();
            assertTrue(metrics.droppedFrames() > 0, "Queue must record dropped frames under backpressure");
        }
    }

    @Test
    @DisplayName("Non-fatal RoadDamageException increments error counter and continues processing")
    void recoversFromRoadDamageException() throws Exception {
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 1, 16, 0.40f, true);
        List<FrameResult> results = new CopyOnWriteArrayList<>();
        AtomicInteger count = new AtomicInteger(0);

        InspectionPipeline.Stage1Function s1 = mat -> {
            int current = count.incrementAndGet();
            if (current == 2) {
                throw new RoadDamageException("stage1", "Transient tensor error on frame 2", null);
            }
            return new MobileNetV3Screener.Stage1Result(0.10f, 0.90f, false, 500_000L);
        };

        try (InspectionPipeline pipeline = new InspectionPipeline(config, results::add, snap -> {}, s1,
                mat -> new YoloV8Detector.Stage2DetectionResult(List.of(), 0L, 0L),
                (mat, d) -> List.of());
             FrameSource source = new MockFrameSource(4)) {
            pipeline.run(source);

            // 4 frames total, 1 failed, 3 succeeded
            assertEquals(3, results.size());
            PipelineMetrics.Snapshot metrics = pipeline.metrics().snapshot();
            assertEquals(1L, metrics.errors());
            assertEquals(3L, metrics.framesSkippedStage2());
            assertEquals(0L, metrics.framesOut());
        }
    }

    @Test
    @DisplayName("Fatal RuntimeException cancels pipeline and throws wrapped InferenceException")
    void fatalRuntimeExceptionCancelsAndThrows() {
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 1, 16, 0.40f, true);

        InspectionPipeline.Stage1Function s1 = mat -> {
            throw new IllegalStateException("Simulated fatal runtime bug");
        };

        try (InspectionPipeline pipeline = new InspectionPipeline(config, res -> {}, snap -> {}, s1,
                mat -> new YoloV8Detector.Stage2DetectionResult(List.of(), 0L, 0L),
                (mat, d) -> List.of());
             FrameSource source = new MockFrameSource(5)) {

            InferenceException ex = assertThrows(InferenceException.class, () -> pipeline.run(source));
            assertNotNull(ex);
            assertTrue(ex.getMessage().contains("Fatal"));
            assertEquals(1L, pipeline.metrics().snapshot().errors());
            assertFalse(pipeline.isRunning());
        }
    }

    @Test
    @DisplayName("Cancel and close methods are idempotent and release cleanly")
    void cancelAndCloseIdempotent() {
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 1, 16, 0.40f, true);

        InspectionPipeline pipeline = new InspectionPipeline(config, res -> {}, snap -> {},
                mat -> new MobileNetV3Screener.Stage1Result(0.1f, 0.9f, false, 0L),
                mat -> new YoloV8Detector.Stage2DetectionResult(List.of(), 0L, 0L),
                (mat, d) -> List.of());

        assertTrue(pipeline.isRunning());
        assertDoesNotThrow(pipeline::stop);
        assertFalse(pipeline.isRunning());
        assertDoesNotThrow(pipeline::cancel);
        assertDoesNotThrow(pipeline::close);
        assertDoesNotThrow(pipeline::close);
    }

    @Test
    @DisplayName("NativeLeakAudit confirms zero native memory leaks after pipeline execution and close")
    void nativeLeakAuditConfirmsZeroLeaks() throws Exception {
        PipelineConfig config = new PipelineConfig(MOCK_DIR, 1, 16, 0.40f, true);

        InspectionPipeline.Stage1Function s1 = mat ->
                new MobileNetV3Screener.Stage1Result(0.75f, 0.25f, true, 500_000L);
        Detection det = new Detection(new BoundingBox(5, 5, 25, 25), DamageClass.D00_LONGITUDINAL, 0.88f);
        InspectionPipeline.Stage2Function s2 = mat ->
                new YoloV8Detector.Stage2DetectionResult(List.of(det), 500_000L, 500_000L);
        DamageAssessment da = new DamageAssessment(
                det, 0.8f, 0.15f, 0.05f, 0.02f, 2.5f, 20, SeverityLevel.LOW, "Minor crack"
        );
        InspectionPipeline.Stage3Function s3 = (mat, d) -> List.of(da);

        try (InspectionPipeline pipeline = new InspectionPipeline(config, res -> {}, snap -> {}, s1, s2, s3);
             FrameSource source = new MockFrameSource(10)) {
            pipeline.run(source);
        }

        NativeLeakAudit.Snapshot audit = NativeLeakAudit.snapshot();
        assertEquals(0L, audit.openTensorLeases(), "TensorLease open leases must return to 0");
        assertEquals(0L, audit.activeManagedMats(), "ManagedMat active instances must return to 0");
    }
}
