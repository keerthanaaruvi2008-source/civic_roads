package com.roaddamage.ui;

import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import com.roaddamage.core.model.SeverityLevel;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FrameSnapshotUnitTest {

    @BeforeAll
    static void initJavaFx() {
        OpenCV.loadLocally();
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @Test
    void validatesImmutabilityAndAccessors() {
        WritableImage orig = new WritableImage(100, 100);
        WritableImage anno = new WritableImage(100, 100);

        Detection detection = new Detection(new BoundingBox(10.0f, 10.0f, 40.0f, 40.0f), DamageClass.D40_POTHOLE, 0.95f);
        DamageAssessment assessment = new DamageAssessment(
                detection, 0.05f, 0.15f, 0.80f, 0.02f, 1.0f, 88, SeverityLevel.CRITICAL, "Critical pothole"
        );

        FrameSnapshot snapshot = new FrameSnapshot(100L, 3300L, orig, anno, List.of(assessment));
        assertEquals(100L, snapshot.frameIndex());
        assertEquals(3300L, snapshot.timestampMillis());
        assertEquals(orig, snapshot.original());
        assertEquals(anno, snapshot.annotated());
        assertEquals(1, snapshot.assessments().size());
        assertEquals(assessment, snapshot.assessments().get(0));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.assessments().add(assessment));
    }

    @Test
    void rejectsNullParameters() {
        WritableImage img = new WritableImage(10, 10);
        assertThrows(NullPointerException.class, () -> new FrameSnapshot(0L, 0L, null, img, List.of()));
        assertThrows(NullPointerException.class, () -> new FrameSnapshot(0L, 0L, img, null, List.of()));
        assertThrows(NullPointerException.class, () -> FrameSnapshot.matToWritableImage(null));
    }

    @Test
    void convertsMatToWritableImageOffFxThread() {
        Mat mat = new Mat(50, 60, CvType.CV_8UC3, new Scalar(100, 120, 140));
        try {
            WritableImage image = FrameSnapshot.matToWritableImage(mat);
            assertNotNull(image);
            assertEquals(60.0, image.getWidth());
            assertEquals(50.0, image.getHeight());
        } finally {
            mat.release();
        }
    }
}
