package com.roaddamage.stage3;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EfficientNetSeverityClassifierTest {

    @BeforeAll
    static void init() {
        OpenCV.loadLocally();
    }

    @Test
    void rejectsNullArguments() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2)) {
            assertThrows(NullPointerException.class, () -> new EfficientNetSeverityClassifier(null, pool));
            assertThrows(NullPointerException.class,
                    () -> new EfficientNetSeverityClassifier(Path.of("models", "stage3_efficientnet.onnx"), null));
        }
    }

    @Test
    void missingModelFileThrowsInferenceException() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2)) {
            Path missing = Path.of("models", "non_existent_stage3.onnx");
            InferenceException ex = assertThrows(InferenceException.class, () -> new EfficientNetSeverityClassifier(missing, pool));
            assertTrue(ex.getMessage().contains("missing or unreadable")
                    || (ex.context().containsKey("error") && ex.context().get("error").toString().contains("missing")));
        }
    }

    @Test
    void endToEndClassificationIfModelArtifactPresent() throws Exception {
        Path modelPath = Path.of("models", "stage3_efficientnet.onnx");
        Assumptions.assumeTrue(Files.isRegularFile(modelPath),
                "Skipping end-to-end severity test: model artifact not present at " + modelPath);

        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(2);
             EfficientNetSeverityClassifier classifier = new EfficientNetSeverityClassifier(modelPath, pool)) {

            Mat crop = new Mat(224, 224, CvType.CV_8UC3, new Scalar(100, 150, 200));
            try {
                assertThrows(NullPointerException.class, () -> classifier.classify(null));

                float[] probs = classifier.classify(crop);
                assertNotNull(probs);
                assertEquals(3, probs.length);

                float sum = probs[0] + probs[1] + probs[2];
                assertEquals(1.0f, sum, 1e-3f);
                assertTrue(probs[0] >= 0.0f && probs[0] <= 1.0f);
                assertTrue(probs[1] >= 0.0f && probs[1] <= 1.0f);
                assertTrue(probs[2] >= 0.0f && probs[2] <= 1.0f);
            } finally {
                crop.release();
            }

            classifier.close();
            // Verify close idempotency
            classifier.close();

            Mat crop2 = new Mat(50, 50, CvType.CV_8UC3, new Scalar(0, 0, 0));
            try {
                assertThrows(IllegalStateException.class, () -> classifier.classify(crop2));
            } finally {
                crop2.release();
            }
        }
    }
}
