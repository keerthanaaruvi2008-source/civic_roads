package com.roaddamage.stage2;

import com.roaddamage.core.error.InferenceException;
import com.roaddamage.core.model.Detection;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YoloV8DetectorTest {

    @BeforeAll
    static void init() {
        OpenCV.loadLocally();
    }

    @Test
    void rejectsNullArguments() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2)) {
            assertThrows(NullPointerException.class, () -> new YoloV8Detector(null, pool));
            assertThrows(NullPointerException.class,
                    () -> new YoloV8Detector(Path.of("models", "stage2_yolov8.onnx"), null));
        }
    }

    @Test
    void missingModelFileThrowsInferenceException() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2)) {
            Path missing = Path.of("models", "non_existent_stage2.onnx");
            InferenceException ex = assertThrows(InferenceException.class, () -> new YoloV8Detector(missing, pool));
            assertTrue(ex.getMessage().contains("missing or unreadable")
                    || (ex.context().containsKey("error") && ex.context().get("error").toString().contains("missing")));
        }
    }

    @Test
    void endToEndDetectionIfModelArtifactPresent() throws Exception {
        Path modelPath = Path.of("models", "stage2_yolov8.onnx");
        Assumptions.assumeTrue(Files.isRegularFile(modelPath),
                "Skipping end-to-end detection test: model artifact not present at " + modelPath);

        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2);
             YoloV8Detector detector = new YoloV8Detector(modelPath, pool)) {

            Mat frame = new Mat(480, 640, CvType.CV_8UC3, new Scalar(100, 150, 200));
            try {
                List<Detection> detections = detector.detect(frame);
                assertNotNull(detections);

                YoloV8Detector.Stage2DetectionResult res = detector.detectWithMetrics(frame);
                assertNotNull(res);
                assertTrue(res.inferenceNanos() > 0L);
                assertTrue(res.totalNanos() >= res.inferenceNanos());
            } finally {
                frame.release();
            }

            detector.close();
            // Verify close idempotency
            detector.close();

            Mat frame2 = new Mat(100, 100, CvType.CV_8UC3, new Scalar(0, 0, 0));
            try {
                assertThrows(IllegalStateException.class, () -> detector.detect(frame2));
            } finally {
                frame2.release();
            }
        }
    }
}
