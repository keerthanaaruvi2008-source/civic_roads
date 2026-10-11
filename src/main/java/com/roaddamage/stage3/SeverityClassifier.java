package com.roaddamage.stage3;

import com.roaddamage.core.error.InferenceException;
import org.opencv.core.Mat;

/**
 * Functional interface for neural network models classifying pavement distress crops into multi-tier severity probabilities.
 */
@FunctionalInterface
public interface SeverityClassifier extends AutoCloseable {

    /**
     * Evaluates a cropped distress region and outputs classification probabilities for [pLow, pMedium, pHigh].
     *
     * @param bgrCrop OpenCV crop Mat in BGR color format
     * @return 3-element float array containing [pLow, pMedium, pHigh] probabilities summing to 1.0
     * @throws InferenceException if preprocessing or model evaluation fails
     */
    float[] classify(Mat bgrCrop) throws InferenceException;

    /**
     * Closes any native sessions or resources held by the classifier. Default implementation is a no-op.
     */
    @Override
    default void close() {
        // No-op by default
    }
}
