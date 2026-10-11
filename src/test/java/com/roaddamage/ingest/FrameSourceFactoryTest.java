package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrameSourceFactoryTest {

    @TempDir
    Path tmp;

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    @Test
    void opensImageFrameSourceForKnownImageExtensions() throws Exception {
        Path img = tmp.resolve("sample.jpg");
        Mat mat = new Mat(10, 10, CvType.CV_8UC3, new Scalar(0, 0, 0));
        try {
            assertTrue(Imgcodecs.imwrite(img.toString(), mat));
        } finally {
            mat.release();
        }

        try (FrameSource src = FrameSourceFactory.open(img)) {
            assertInstanceOf(ImageFrameSource.class, src);
        }
    }

    @Test
    void rejectsUnsupportedFileExtensions() {
        Path invalid = tmp.resolve("unsupported.txt");
        IngestionException ex = assertThrows(IngestionException.class, () -> FrameSourceFactory.open(invalid));
        assertTrue(ex.context().containsKey("extension"));
    }

    @Test
    void rejectsFilesWithoutExtension() {
        Path noExt = tmp.resolve("filename_without_ext");
        assertThrows(IngestionException.class, () -> FrameSourceFactory.open(noExt));
    }
}
