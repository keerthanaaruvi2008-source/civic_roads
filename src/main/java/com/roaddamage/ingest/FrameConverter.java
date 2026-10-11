package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import com.roaddamage.core.model.ManagedMat;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.Objects;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Preprocessing conversion utility transforming OpenCV BGR frames into planar CHW FloatBuffers for neural inference.
 */
public final class FrameConverter {

    private FrameConverter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts an input BGR Mat to planar CHW float format with optional ImageNet channel normalization.
     *
     * @param bgr input frame in BGR channel order
     * @param targetSize destination spatial width and height
     * @param mean channel normalization mean array [R, G, B] or null
     * @param std channel normalization std array [R, G, B] or null
     * @param destination target direct FloatBuffer
     * @return destination buffer with position set to 0
     * @throws IngestionException if parameters are invalid or buffer capacity is insufficient
     */
    public static FloatBuffer toCHWFloat(Mat bgr, int targetSize, float[] mean, float[] std, FloatBuffer destination)
            throws IngestionException {
        Objects.requireNonNull(bgr, "bgr Mat must not be null");
        if (targetSize <= 0) {
            throw new IngestionException("ingest", "targetSize must be > 0: " + targetSize, null,
                    Map.of("targetSize", targetSize));
        }
        int requiredCapacity = 3 * targetSize * targetSize;
        if (destination == null || destination.capacity() < requiredCapacity) {
            int cap = destination == null ? 0 : destination.capacity();
            throw new IngestionException("ingest",
                    "Destination buffer capacity (" + cap + ") is insufficient for " + requiredCapacity, null,
                    Map.of("capacity", cap, "requiredCapacity", requiredCapacity));
        }

        // Justification: Intermediate Mats for RGB conversion and resizing; managed inside try-with-resources and released automatically on exit.
        try (ManagedMat rgbManaged = new ManagedMat(new Mat());
             ManagedMat resizedManaged = new ManagedMat(new Mat())) {

            Mat rgb = rgbManaged.mat();
            Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB);

            Mat resized = resizedManaged.mat();
            Imgproc.resize(rgb, resized, new Size(targetSize, targetSize), 0, 0, Imgproc.INTER_LINEAR);

            int planeSize = targetSize * targetSize;
            int totalBytes = (int) (resized.total() * resized.channels());
            byte[] scratch = new byte[totalBytes];
            resized.get(0, 0, scratch);

            float[] chw = new float[3 * planeSize];
            boolean normalize = (mean != null && std != null);
            float meanR = (mean != null && mean.length > 0) ? mean[0] : 0.0f;
            float meanG = (mean != null && mean.length > 1) ? mean[1] : 0.0f;
            float meanB = (mean != null && mean.length > 2) ? mean[2] : 0.0f;

            float stdR = (std != null && std.length > 0) ? std[0] : 1.0f;
            float stdG = (std != null && std.length > 1) ? std[1] : 1.0f;
            float stdB = (std != null && std.length > 2) ? std[2] : 1.0f;

            for (int i = 0; i < planeSize; i++) {
                int pixelOffset = i * 3;
                float r = (scratch[pixelOffset] & 0xFF) / 255.0f;
                float g = (scratch[pixelOffset + 1] & 0xFF) / 255.0f;
                float b = (scratch[pixelOffset + 2] & 0xFF) / 255.0f;

                if (normalize) {
                    r = (r - meanR) / stdR;
                    g = (g - meanG) / stdG;
                    b = (b - meanB) / stdB;
                }

                chw[i] = r;
                chw[planeSize + i] = g;
                chw[2 * planeSize + i] = b;
            }

            destination.clear();
            destination.put(chw);
            destination.position(0);
            return destination;
        }
    }
}
