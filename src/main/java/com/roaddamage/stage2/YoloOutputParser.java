package com.roaddamage.stage2;

import com.roaddamage.core.error.InferenceException;
import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance parser decoding raw YOLOv8 3D output tensors into candidate detections.
 */
public final class YoloOutputParser {

    private YoloOutputParser() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Decodes the raw YOLOv8 prediction tensor into bounding box detection candidates.
     *
     * @param output 3D float array with shape [1, 4 + numClasses, N] where N is anchor proposal count
     * @param t letterbox transformation used during preprocessing
     * @param confThreshold minimum class probability required to consider a candidate
     * @param numClasses number of damage classification heads (typically 4 for RDD)
     * @return list of parsed detections in original source image space
     * @throws InferenceException if the output array dimensions or shapes do not conform to YOLO specifications
     * @throws NullPointerException if output or t is null
     */
    public static List<Detection> parse(float[][][] output, Letterbox.LetterboxTransform t,
                                        float confThreshold, int numClasses) throws InferenceException {
        Objects.requireNonNull(output, "output tensor must not be null");
        Objects.requireNonNull(t, "letterbox transform must not be null");

        if (numClasses <= 0) {
            throw new IllegalArgumentException("numClasses must be positive: " + numClasses);
        }

        int expectedChannels = 4 + numClasses;
        if (output.length != 1 || output[0] == null) {
            throw new InferenceException("stage2", "Invalid YOLO output tensor structure", null,
                    Map.of("shape", "rank must be 3 with batch size 1"));
        }

        int actualChannels = output[0].length;
        int actualAnchors = (actualChannels > 0 && output[0][0] != null) ? output[0][0].length : 0;

        if (actualChannels != expectedChannels || actualAnchors == 0) {
            throw new InferenceException("stage2",
                    "expected [1," + expectedChannels + ",N], got [1," + actualChannels + "," + actualAnchors + "]", null,
                    Map.of("expectedChannels", expectedChannels,
                           "actualChannels", actualChannels,
                           "actualAnchors", actualAnchors));
        }

        float[][] tensor2D = output[0];
        float[] cxArr = tensor2D[0];
        float[] cyArr = tensor2D[1];
        float[] wArr = tensor2D[2];
        float[] hArr = tensor2D[3];

        List<Detection> detections = new ArrayList<>();

        for (int i = 0; i < actualAnchors; i++) {
            float maxConf = -1.0f;
            int bestClass = -1;

            for (int c = 0; c < numClasses; c++) {
                float conf = tensor2D[4 + c][i];
                if (conf > maxConf) {
                    maxConf = conf;
                    bestClass = c;
                }
            }

            if (maxConf < confThreshold) {
                continue;
            }

            float w = wArr[i];
            float h = hArr[i];
            if (w <= 0.0f || h <= 0.0f) {
                continue;
            }

            float cx = cxArr[i];
            float cy = cyArr[i];
            float halfW = w * 0.5f;
            float halfH = h * 0.5f;

            BoundingBox dstBox = new BoundingBox(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
            BoundingBox srcBox = t.toSource(dstBox);

            if (srcBox.area() <= 1.0f) {
                continue;
            }

            detections.add(new Detection(srcBox, DamageClass.fromIndex(bestClass), maxConf));
        }

        return Collections.unmodifiableList(detections);
    }
}
