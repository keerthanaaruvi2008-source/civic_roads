package com.roaddamage.stage3;

import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SeverityEngineTest {

    @BeforeAll
    static void initOpenCV() {
        OpenCV.loadLocally();
    }

    @Test
    void validatesConstructorParameters() {
        assertThrows(NullPointerException.class, () -> new SeverityEngine(null, 640, 640));
        assertThrows(IllegalArgumentException.class, () -> new SeverityEngine(crop -> new float[]{0.5f, 0.3f, 0.2f}, 0, 640));
        assertThrows(IllegalArgumentException.class, () -> new SeverityEngine(crop -> new float[]{0.5f, 0.3f, 0.2f}, 640, -1));
    }

    @Test
    void validatesAssessParameters() {
        try (SeverityEngine engine = new SeverityEngine(crop -> new float[]{0.5f, 0.3f, 0.2f}, 640, 640)) {
            Mat frame = new Mat(640, 640, CvType.CV_8UC3);
            try {
                assertThrows(NullPointerException.class, () -> engine.assess(null, List.of()));
                assertThrows(NullPointerException.class, () -> engine.assess(frame, null));
            } finally {
                frame.release();
            }
        }
    }

    @Test
    void emptyDetectionsReturnsEmptyList() throws Exception {
        try (SeverityEngine engine = new SeverityEngine(crop -> new float[]{0.5f, 0.3f, 0.2f}, 640, 640)) {
            Mat frame = new Mat(640, 640, CvType.CV_8UC3);
            try {
                List<DamageAssessment> assessments = engine.assess(frame, List.of());
                assertTrue(assessments.isEmpty());
            } finally {
                frame.release();
            }
        }
    }

    @Test
    void skipsTinyClippedProposals() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger(0);
        SeverityClassifier classifier = crop -> {
            invocationCount.incrementAndGet();
            return new float[]{0.2f, 0.5f, 0.3f};
        };

        try (SeverityEngine engine = new SeverityEngine(classifier, 640, 640)) {
            Mat frame = new Mat(640, 640, CvType.CV_8UC3);
            try {
                // Proposal completely outside or < 4px after clipping
                Detection tinyBox = new Detection(new BoundingBox(639.0f, 639.0f, 640.0f, 640.0f), DamageClass.D40_POTHOLE, 0.9f);
                Detection outsideBox = new Detection(new BoundingBox(700.0f, 700.0f, 800.0f, 800.0f), DamageClass.D40_POTHOLE, 0.9f);

                List<DamageAssessment> results = engine.assess(frame, List.of(tinyBox, outsideBox));
                assertEquals(0, results.size());
                assertEquals(0, invocationCount.get());
            } finally {
                frame.release();
            }
        }
    }

    @Test
    void assessesAndSortsByPriorityScoreDescending() throws Exception {
        SeverityClassifier classifier = crop -> {
            // Assert that crop is a non-empty submat
            assertTrue(crop.width() > 0);
            assertTrue(crop.height() > 0);
            return new float[]{0.1f, 0.2f, 0.7f};
        };

        try (SeverityEngine engine = new SeverityEngine(classifier, 640, 640)) {
            Mat frame = new Mat(640, 640, CvType.CV_8UC3);
            try {
                // Detection 1: small crack
                Detection smallCrack = new Detection(
                        new BoundingBox(10.0f, 10.0f, 30.0f, 30.0f),
                        DamageClass.D00_LONGITUDINAL,
                        0.80f
                );

                // Detection 2: larger pothole (should score higher)
                Detection severePothole = new Detection(
                        new BoundingBox(50.0f, 50.0f, 250.0f, 250.0f),
                        DamageClass.D40_POTHOLE,
                        0.95f
                );

                List<DamageAssessment> assessments = engine.assess(frame, List.of(smallCrack, severePothole));
                assertEquals(2, assessments.size());

                DamageAssessment first = assessments.get(0);
                DamageAssessment second = assessments.get(1);

                // Sorted descending
                assertTrue(first.priorityScore() >= second.priorityScore());
                assertEquals(DamageClass.D40_POTHOLE, first.detection().damageClass());
                assertEquals(DamageClass.D00_LONGITUDINAL, second.detection().damageClass());
            } finally {
                frame.release();
            }
        }
    }

    @Test
    void closeDelegatesToClassifier() {
        AtomicBoolean closed = new AtomicBoolean(false);
        SeverityClassifier classifier = new SeverityClassifier() {
            @Override
            public float[] classify(Mat bgrCrop) {
                return new float[]{0.33f, 0.33f, 0.34f};
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        SeverityEngine engine = new SeverityEngine(classifier, 640, 640);
        engine.close();
        assertTrue(closed.get());
    }
}
