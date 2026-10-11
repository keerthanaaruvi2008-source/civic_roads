package com.roaddamage.pipeline;

import com.roaddamage.config.ModelConfig;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration parameters for the end-to-end multi-stage inspection pipeline.
 *
 * @param modelDir directory containing ONNX model artifacts
 * @param frameStride frame subsampling stride (1 processes every frame, 2 every other, etc.)
 * @param queueCapacity bounded in-memory frame buffer capacity in [1, 64]
 * @param stage1Threshold damage probability threshold in [0.0, 1.0] for Stage 1 screening
 * @param skipStage2WhenClean if true, bypasses Stage 2 & 3 when Stage 1 classifies frame as undamaged
 */
public record PipelineConfig(
        Path modelDir,
        int frameStride,
        int queueCapacity,
        float stage1Threshold,
        boolean skipStage2WhenClean) {

    /**
     * Compact constructor validating configuration constraints.
     */
    public PipelineConfig {
        Objects.requireNonNull(modelDir, "modelDir must not be null");
        if (frameStride < 1) {
            throw new IllegalArgumentException("frameStride must be >= 1: " + frameStride);
        }
        if (queueCapacity < 1 || queueCapacity > 64) {
            throw new IllegalArgumentException("queueCapacity must be in [1, 64]: " + queueCapacity);
        }
        if (stage1Threshold < 0.0f || stage1Threshold > 1.0f) {
            throw new IllegalArgumentException("stage1Threshold must be in [0.0, 1.0]: " + stage1Threshold);
        }
    }

    /**
     * Creates a default PipelineConfig using standard framework thresholds.
     *
     * @param modelDir path to ONNX model weights directory
     * @return default PipelineConfig instance
     */
    public static PipelineConfig defaults(Path modelDir) {
        return new PipelineConfig(
                modelDir,
                1,
                16,
                ModelConfig.STAGE1_DAMAGE_THRESHOLD,
                true
        );
    }
}
