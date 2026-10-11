package com.roaddamage.ui;

import com.roaddamage.core.model.DamageAssessment;
import java.util.List;
import java.util.Objects;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/**
 * Immutable snapshot encapsulating frame imagery and associated road distress evaluations.
 * <p>
 * {@link WritableImage} instances are constructed off the JavaFX application thread using a
 * direct {@code Mat} BGR to BGRA byte array conversion without SwingFXUtils or BufferedImage dependencies.
 *
 * @param frameIndex sequence index of source video frame
 * @param timestampMillis capture or video timestamp in milliseconds
 * @param original source frame image
 * @param annotated annotated frame image highlighting detections
 * @param assessments unmodifiable list of evaluated damage assessments
 */
public record FrameSnapshot(
        long frameIndex,
        long timestampMillis,
        WritableImage original,
        WritableImage annotated,
        List<DamageAssessment> assessments) {

    /**
     * Compact constructor enforcing immutability and non-null guarantees.
     */
    public FrameSnapshot {
        Objects.requireNonNull(original, "original image must not be null");
        Objects.requireNonNull(annotated, "annotated image must not be null");
        assessments = (assessments == null) ? List.of() : List.copyOf(assessments);
    }

    /**
     * Converts an OpenCV BGR {@link Mat} to a JavaFX {@link WritableImage} off the FX thread.
     * <p>
     * Memory safety: Allocates a temporary BGRA Mat released in {@code finally}, avoiding
     * AWT/Swing dependencies and native leaks.
     *
     * @param bgrMat source OpenCV matrix in BGR format
     * @return freshly constructed WritableImage
     * @throws NullPointerException if bgrMat is null
     */
    public static WritableImage matToWritableImage(Mat bgrMat) {
        Objects.requireNonNull(bgrMat, "bgrMat must not be null");

        int width = bgrMat.cols();
        int height = bgrMat.rows();
        if (width <= 0 || height <= 0) {
            return new WritableImage(1, 1);
        }

        Mat bgraMat = new Mat();
        try {
            int channels = bgrMat.channels();
            if (channels == 3) {
                Imgproc.cvtColor(bgrMat, bgraMat, Imgproc.COLOR_BGR2BGRA);
            } else if (channels == 1) {
                Imgproc.cvtColor(bgrMat, bgraMat, Imgproc.COLOR_GRAY2BGRA);
            } else if (channels == 4) {
                bgrMat.copyTo(bgraMat);
            } else {
                Imgproc.cvtColor(bgrMat, bgraMat, Imgproc.COLOR_BGR2BGRA);
            }

            byte[] bgraBytes = new byte[width * height * 4];
            bgraMat.get(0, 0, bgraBytes);

            WritableImage image = new WritableImage(width, height);
            image.getPixelWriter().setPixels(
                    0, 0, width, height,
                    PixelFormat.getByteBgraInstance(),
                    bgraBytes, 0, width * 4
            );
            return image;
        } finally {
            bgraMat.release();
        }
    }
}
