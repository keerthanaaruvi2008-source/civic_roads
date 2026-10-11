package com.roaddamage.ingest;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MatBufferedImageTest {

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    @Test
    void convertsBgrMatToBufferedImage() {
        Mat mat = new Mat(20, 30, CvType.CV_8UC3, new Scalar(10, 20, 30));
        try {
            BufferedImage img = MatBufferedImage.toBufferedImage(mat);
            assertNotNull(img);
            assertEquals(30, img.getWidth());
            assertEquals(20, img.getHeight());
        } finally {
            mat.release();
        }
    }

    @Test
    void rejectsEmptyMat() {
        Mat mat = new Mat();
        try {
            assertThrows(IllegalArgumentException.class, () -> MatBufferedImage.toBufferedImage(mat));
        } finally {
            mat.release();
        }
    }
}
