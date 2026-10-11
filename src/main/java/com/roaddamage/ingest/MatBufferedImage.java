package com.roaddamage.ingest;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Objects;
import org.opencv.core.Mat;

/**
 * Utility for converting OpenCV {@link Mat} instances to Java {@link BufferedImage} for test assertions and debug tooling.
 * Strictly prohibited on the inference pipeline hot path.
 */
public final class MatBufferedImage {

    private MatBufferedImage() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts a BGR (CV_8UC3) or Grayscale (CV_8UC1) OpenCV Mat to an AWT BufferedImage.
     *
     * @param mat the source Mat
     * @return the resulting BufferedImage
     * @throws IllegalArgumentException if mat is empty or not 1 or 3 channels
     */
    public static BufferedImage toBufferedImage(Mat mat) {
        Objects.requireNonNull(mat, "mat must not be null");
        if (mat.empty()) {
            throw new IllegalArgumentException("Cannot convert empty Mat to BufferedImage");
        }
        int channels = mat.channels();
        int type = (channels == 1) ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, targetPixels);
        return image;
    }
}
