package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import com.roaddamage.core.model.FrameEnvelope;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ImageFrameSourceTest {

    @TempDir
    Path tmp;

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    private Path writeSampleImage(String name) {
        Path out = tmp.resolve(name);
        Mat mat = new Mat(32, 32, CvType.CV_8UC3, new Scalar(100, 150, 200));
        try {
            assertTrue(Imgcodecs.imwrite(out.toString(), mat));
        } finally {
            mat.release();
        }
        return out;
    }

    @Test
    void readsSingleFrameAndExhausts() throws Exception {
        Path imgPath = writeSampleImage("test.png");
        try (ImageFrameSource src = new ImageFrameSource(imgPath)) {
            assertEquals(0.0, src.fps(), 1e-6);
            assertEquals(1, src.totalFrames());
            assertEquals(imgPath.toString(), src.sourceId());

            Optional<FrameEnvelope> envOpt = src.next();
            assertTrue(envOpt.isPresent());
            try (FrameEnvelope env = envOpt.get()) {
                assertEquals(0L, env.frameIndex());
                assertEquals(0L, env.timestampMillis());
                assertNotNull(env.frame());
                assertFalse(env.frame().isReleased());
                assertEquals(32, env.frame().mat().rows());
                assertEquals(32, env.frame().mat().cols());
            }

            Optional<FrameEnvelope> second = src.next();
            assertFalse(second.isPresent());
        }
    }

    @Test
    void nonExistentFileThrowsIngestionException() {
        Path missing = tmp.resolve("not_found.png");
        IngestionException ex = assertThrows(IngestionException.class, () -> new ImageFrameSource(missing));
        assertTrue(ex.context().containsKey("path"));
    }

    @Test
    void closeIsIdempotent() {
        Path imgPath = writeSampleImage("test_close.png");
        ImageFrameSource src = new ImageFrameSource(imgPath);
        src.close();
        assertDoesNotThrow(src::close);
    }
}
