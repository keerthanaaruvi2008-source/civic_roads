package com.roaddamage.ingest;

import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.error.IngestionException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FrameConverterTest {

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    @Test
    void solidRedBgrImageBecomesRedChannelOneInCHW() {
        int size = 4;
        Mat bgr = new Mat(size, size, CvType.CV_8UC3, new Scalar(0, 0, 255));
        try {
            FloatBuffer dst = ByteBuffer.allocateDirect(3 * size * size * Float.BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            FrameConverter.toCHWFloat(bgr, size, null, null, dst);
            for (int i = 0; i < size * size; i++) {
                assertEquals(1.0f, dst.get(i), 1e-3f, "R channel");
                assertEquals(0.0f, dst.get(size * size + i), 1e-3f, "G channel");
                assertEquals(0.0f, dst.get(2 * size * size + i), 1e-3f, "B channel");
            }
        } finally {
            bgr.release();
        }
    }

    @Test
    void normalizationAppliesMeanAndStd() {
        int size = 2;
        Mat bgr = new Mat(size, size, CvType.CV_8UC3, new Scalar(0, 0, 0));
        try {
            FloatBuffer dst = ByteBuffer.allocateDirect(3 * size * size * Float.BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            FrameConverter.toCHWFloat(bgr, size, ModelConfig.IMAGENET_MEAN, ModelConfig.IMAGENET_STD, dst);
            assertEquals((0f - 0.485f) / 0.229f, dst.get(0), 1e-3f);
        } finally {
            bgr.release();
        }
    }

    @Test
    void undersizedDestinationIsRejected() {
        Mat bgr = new Mat(8, 8, CvType.CV_8UC3, new Scalar(0, 0, 0));
        try {
            FloatBuffer tiny = FloatBuffer.allocate(10);
            assertThrows(IngestionException.class,
                    () -> FrameConverter.toCHWFloat(bgr, 8, null, null, tiny));
        } finally {
            bgr.release();
        }
    }
}
