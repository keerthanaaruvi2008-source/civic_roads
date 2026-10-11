package com.roaddamage.stage2;

import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.ManagedMat;
import java.nio.FloatBuffer;
import java.util.Objects;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Aspect-ratio preserving letterboxing and normalization preprocessor for YOLOv8 object detection.
 */
public final class Letterbox {

    private Letterbox() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Geometry transformation mapping coordinates between the letterboxed canvas space and original source image.
     *
     * @param scale scaling factor applied to source dimensions
     * @param padX horizontal padding in pixels added to each side
     * @param padY vertical padding in pixels added to each side
     * @param srcWidth source image width in pixels
     * @param srcHeight source image height in pixels
     * @param dstSize square letterbox canvas dimension in pixels
     */
    public record LetterboxTransform(float scale, int padX, int padY, int srcWidth, int srcHeight, int dstSize) {

        /**
         * Transforms a bounding box from the letterboxed canvas coordinate space back to the source image space.
         *
         * @param dstBox bounding box coordinates in the letterboxed canvas [0, dstSize]
         * @return clipped bounding box coordinates in source image space [0, srcWidth] x [0, srcHeight]
         * @throws NullPointerException if dstBox is null
         */
        public BoundingBox toSource(BoundingBox dstBox) {
            Objects.requireNonNull(dstBox, "dstBox must not be null");
            float x1 = (dstBox.x1() - (float) padX) / scale;
            float y1 = (dstBox.y1() - (float) padY) / scale;
            float x2 = (dstBox.x2() - (float) padX) / scale;
            float y2 = (dstBox.y2() - (float) padY) / scale;
            return new BoundingBox(x1, y1, x2, y2).clipTo(srcWidth, srcHeight);
        }
    }

    /**
     * Computes the scaling factor and symmetric padding dimensions for letterboxing to a square destination size.
     *
     * @param srcW source width in pixels
     * @param srcH source height in pixels
     * @param dst destination square size in pixels
     * @return computed LetterboxTransform
     * @throws IllegalArgumentException if dimensions are not positive
     */
    public static LetterboxTransform compute(int srcW, int srcH, int dst) {
        if (srcW <= 0 || srcH <= 0 || dst <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive: src=" + srcW + "x" + srcH + ", dst=" + dst);
        }

        float scale = Math.min((float) dst / srcW, (float) dst / srcH);
        int newW = Math.round((float) srcW * scale);
        int newH = Math.round((float) srcH * scale);
        int padX = (dst - newW) / 2;
        int padY = (dst - newH) / 2;

        return new LetterboxTransform(scale, padX, padY, srcW, srcH, dst);
    }

    /**
     * Resizes the input BGR frame preserving aspect ratio, pads with constant 114 grey, converts to RGB,
     * normalizes to [0, 1], and copies planar CHW floats into the direct output buffer.
     *
     * @param bgr source OpenCV frame in BGR format
     * @param dst target square canvas dimension in pixels (e.g., 640)
     * @param out direct float destination buffer with capacity at least 3 * dst * dst
     * @return applied LetterboxTransform
     * @throws NullPointerException if bgr or out is null
     * @throws IllegalArgumentException if bgr is empty or out capacity is insufficient
     */
    public static LetterboxTransform applyToFloatCHW(Mat bgr, int dst, FloatBuffer out) {
        Objects.requireNonNull(bgr, "bgr must not be null");
        Objects.requireNonNull(out, "out must not be null");

        if (bgr.empty()) {
            throw new IllegalArgumentException("Input Mat must not be empty");
        }

        int requiredCapacity = 3 * dst * dst;
        if (out.capacity() < requiredCapacity) {
            throw new IllegalArgumentException("Output FloatBuffer capacity (" + out.capacity()
                    + ") is less than required (" + requiredCapacity + ")");
        }

        LetterboxTransform transform = compute(bgr.cols(), bgr.rows(), dst);
        int newW = Math.round((float) bgr.cols() * transform.scale());
        int newH = Math.round((float) bgr.rows() * transform.scale());

        int top = transform.padY();
        int bottom = dst - newH - top;
        int left = transform.padX();
        int right = dst - newW - left;

        // Justification: Intermediate Mats for resizing, border padding, and color space conversion;
        // managed inside try-with-resources and released automatically on exit.
        try (ManagedMat resizedM = new ManagedMat(new Mat());
             ManagedMat paddedM = new ManagedMat(new Mat());
             ManagedMat rgbM = new ManagedMat(new Mat())) {

            Mat resized = resizedM.mat();
            Imgproc.resize(bgr, resized, new Size(newW, newH), 0, 0, Imgproc.INTER_LINEAR);

            Mat padded = paddedM.mat();
            Core.copyMakeBorder(resized, padded, top, bottom, left, right, Core.BORDER_CONSTANT,
                    new Scalar(114, 114, 114));

            Mat rgb = rgbM.mat();
            Imgproc.cvtColor(padded, rgb, Imgproc.COLOR_BGR2RGB);

            int planeSize = dst * dst;
            int totalBytes = planeSize * 3;
            byte[] bytes = new byte[totalBytes];
            rgb.get(0, 0, bytes);

            int rOffset = 0;
            int gOffset = planeSize;
            int bOffset = 2 * planeSize;

            for (int i = 0; i < planeSize; i++) {
                int idx = i * 3;
                float r = (bytes[idx] & 0xFF) / 255.0f;
                float g = (bytes[idx + 1] & 0xFF) / 255.0f;
                float b = (bytes[idx + 2] & 0xFF) / 255.0f;

                out.put(rOffset + i, r);
                out.put(gOffset + i, g);
                out.put(bOffset + i, b);
            }

            out.position(0);
            out.limit(requiredCapacity);

            return transform;
        }
    }
}
