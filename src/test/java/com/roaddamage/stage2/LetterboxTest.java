package com.roaddamage.stage2;

import com.roaddamage.core.model.BoundingBox;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LetterboxTest {

    @BeforeAll
    static void init() {
        OpenCV.loadLocally();
    }

    @Test
    void computesGeometryForWideImage() {
        Letterbox.LetterboxTransform t = Letterbox.compute(1920, 1080, 640);
        assertEquals(640.0f / 1920.0f, t.scale(), 1e-5f);
        assertEquals(0, t.padX());
        assertEquals(140, t.padY());
        assertEquals(1920, t.srcWidth());
        assertEquals(1080, t.srcHeight());
        assertEquals(640, t.dstSize());
    }

    @Test
    void toSourceMapsCoordinatesCorrectly() {
        Letterbox.LetterboxTransform t = Letterbox.compute(1920, 1080, 640);
        // Canvas box covering the entire letterboxed image region
        BoundingBox canvasBox = new BoundingBox(0.0f, 140.0f, 640.0f, 500.0f);
        BoundingBox srcBox = t.toSource(canvasBox);

        assertEquals(0.0f, srcBox.x1(), 0.5f);
        assertEquals(0.0f, srcBox.y1(), 0.5f);
        assertEquals(1920.0f, srcBox.x2(), 0.5f);
        assertEquals(1080.0f, srcBox.y2(), 0.5f);
    }

    @Test
    void applyToFloatCHWProducesNormalizedPlanarBuffer() {
        Mat bgr = new Mat(100, 200, CvType.CV_8UC3, new Scalar(50, 100, 150));
        try {
            int dst = 640;
            FloatBuffer out = ByteBuffer.allocateDirect(3 * dst * dst * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();

            Letterbox.LetterboxTransform t = Letterbox.applyToFloatCHW(bgr, dst, out);
            assertEquals(dst, t.dstSize());
            assertEquals(3 * dst * dst, out.limit());
            assertEquals(0, out.position());

            // Check that all values are in normalized [0, 1] range
            for (int i = 0; i < 1000; i++) {
                float val = out.get(i);
                assertTrue(val >= 0.0f && val <= 1.0f);
            }
        } finally {
            bgr.release();
        }
    }
}
