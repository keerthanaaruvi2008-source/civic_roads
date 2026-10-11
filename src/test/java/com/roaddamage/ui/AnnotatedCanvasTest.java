package com.roaddamage.ui;

import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import com.roaddamage.core.model.SeverityLevel;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnnotatedCanvasTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @Test
    void rendersSnapshotAndComputesGeometry() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                AnnotatedCanvas canvas = new AnnotatedCanvas(640, 480);
                WritableImage orig = new WritableImage(1280, 720);
                WritableImage anno = new WritableImage(1280, 720);

                Detection det = new Detection(new BoundingBox(100.0f, 100.0f, 400.0f, 300.0f), DamageClass.D40_POTHOLE, 0.92f);
                DamageAssessment assessment = new DamageAssessment(
                        det, 0.05f, 0.15f, 0.80f, 0.04f, 1.5f, 90, SeverityLevel.CRITICAL, "Emergency pothole"
                );

                FrameSnapshot snapshot = new FrameSnapshot(1L, 33L, orig, anno, List.of(assessment));
                canvas.render(snapshot);

                // Check aspect-ratio scaling (640/1280 = 0.5, 480/720 = 0.666 -> scale is 0.5)
                assertEquals(0.5, canvas.scale(), 1e-4);
                assertEquals(0.0, canvas.offsetX(), 1e-4);
                assertEquals((480.0 - 720.0 * 0.5) / 2.0, canvas.offsetY(), 1e-4);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("Canvas rendering failed on FX thread", failure.get());
        }
    }

    @Test
    void handlesNullSnapshotGracefully() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            AnnotatedCanvas canvas = new AnnotatedCanvas(300, 200);
            assertDoesNotThrow(() -> canvas.render(null));
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
