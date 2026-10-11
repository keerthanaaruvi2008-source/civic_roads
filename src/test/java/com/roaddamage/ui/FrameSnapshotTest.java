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

final class FrameSnapshotTest {

    @BeforeAll
    static void initNativesAndJavaFx() {
        OpenCV.loadLocally();
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already started
        }
    }

    @Test
    void rejectsNullImages() {
        assertThrows(NullPointerException.class, () -> new FrameSnapshot(0L, 0L, null, new WritableImage(10, 10), List.of()));
        assertThrows(NullPointerException.class, () -> new FrameSnapshot(0L, 0L, new WritableImage(10, 10), null, List.of()));
    }

    @Test
    void convertsMatToWritableImageWithoutAwt() {
        Mat mat = new Mat(100, 200, CvType.CV_8UC3, new Scalar(50, 100, 150));
        try {
            WritableImage image = FrameSnapshot.matToWritableImage(mat);
            assertNotNull(image);
            assertEquals(200.0, image.getWidth());
            assertEquals(100.0, image.getHeight());
        } finally {
            mat.release();
        }
    }

    @Test
    void createsImmutableSnapshot() {
        WritableImage orig = new WritableImage(160, 120);
        WritableImage anno = new WritableImage(160, 120);

        Detection detection = new Detection(new BoundingBox(10.0f, 10.0f, 50.0f, 50.0f), DamageClass.D40_POTHOLE, 0.90f);
        DamageAssessment assessment = new DamageAssessment(
                detection, 0.1f, 0.2f, 0.7f, 0.05f, 1.0f, 85, SeverityLevel.CRITICAL, "High risk"
        );

        FrameSnapshot snapshot = new FrameSnapshot(42L, 1400L, orig, anno, List.of(assessment));
        assertEquals(42L, snapshot.frameIndex());
        assertEquals(1400L, snapshot.timestampMillis());
        assertEquals(1, snapshot.assessments().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.assessments().add(assessment));
    }
}
