package com.roaddamage.stage1;

import com.roaddamage.core.error.InferenceException;
import com.roaddamage.infer.DirectFloatBufferPool;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MobileNetV3ScreenerTest {

    @BeforeAll
    static void init() {
        OpenCV.loadLocally();
    }

    @Test
    void rejectsNullArguments() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2)) {
            assertThrows(NullPointerException.class, () -> new MobileNetV3Screener(null, pool));
            assertThrows(NullPointerException.class,
                    () -> new MobileNetV3Screener(Path.of("models", "stage1_mobilenetv3_small.onnx"), null));
        }
    }

    @Test
    void missingModelFileThrowsInferenceException() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2)) {
            Path missing = Path.of("models", "non_existent_stage1.onnx");
            InferenceException ex = assertThrows(InferenceException.class, () -> new MobileNetV3Screener(missing, pool));
            assertTrue(ex.getMessage().contains("missing or unreadable")
                    || (ex.context().containsKey("error") && ex.context().get("error").toString().contains("missing")));
        }
    }

    @Test
    void endToEndScreeningIfModelArtifactPresent() throws Exception {
        Path modelPath = Path.of("models", "stage1_mobilenetv3_small.onnx");
        Assumptions.assumeTrue(Files.isRegularFile(modelPath),
                "Skipping end-to-end inference test: model artifact not present at " + modelPath);

        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2);
             MobileNetV3Screener screener = new MobileNetV3Screener(modelPath, pool)) {

            Mat frame = new Mat(300, 400, CvType.CV_8UC3, new Scalar(100, 150, 200));
            try {
                MobileNetV3Screener.Stage1Result result = screener.screen(frame);
                assertNotNull(result);
                assertTrue(result.inferenceNanos() > 0L);
                assertTrue(result.damageProbability() >= 0.0f && result.damageProbability() <= 1.0f);
                assertTrue(result.cleanProbability() >= 0.0f && result.cleanProbability() <= 1.0f);
            } finally {
                frame.release();
            }

            screener.close();
            // Verify close idempotency
            screener.close();

            Mat frame2 = new Mat(100, 100, CvType.CV_8UC3, new Scalar(0, 0, 0));
            try {
                assertThrows(IllegalStateException.class, () -> screener.screen(frame2));
            } finally {
                frame2.release();
            }
        }
    }

    @Test
    void stage1StressTest500FramesReusesBuffersAndClosesLeases() throws Exception {
        Path modelPath = Path.of("models", "stage1_mobilenetv3_small.onnx");
        Assumptions.assumeTrue(Files.isRegularFile(modelPath),
                "Skipping Stage 1 stress test: model artifact not present at " + modelPath);

        Stage1Metrics metrics = new Stage1Metrics();
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2);
             MobileNetV3Screener screener = new MobileNetV3Screener(modelPath, pool)) {

            Mat frame = new Mat(224, 224, CvType.CV_8UC3, new Scalar(120, 130, 140));
            try {
                for (int i = 0; i < 500; i++) {
                    MobileNetV3Screener.Stage1Result res = screener.screen(frame);
                    metrics.record(res);
                }
            } finally {
                frame.release();
            }

            assertTrue(pool.allocationCount() <= 2L,
                    "Expected buffer pool allocationCount <= 2 after 500 screen() calls, got " + pool.allocationCount());
            org.junit.jupiter.api.Assertions.assertEquals(0L, com.roaddamage.infer.TensorLease.OPEN_LEASES.get(),
                    "TensorLease.OPEN_LEASES must be 0 at end of Stage 1 stress test");
        }

        Stage1Metrics.Snapshot snapshot = metrics.snapshot();
        org.junit.jupiter.api.Assertions.assertEquals(500L, snapshot.count());
    }
}
