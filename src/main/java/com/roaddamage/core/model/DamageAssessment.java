package com.roaddamage.core.model;

import java.util.Objects;

/**
 * Comprehensive pavement damage evaluation aggregating spatial detection, classification probabilities,
 * physical geometry metrics, remediation priority scoring, and diagnostic explanation.
 *
 * @param detection localized road damage proposal
 * @param severityProbLow model probability for low severity tier in [0.0, 1.0]
 * @param severityProbMedium model probability for medium severity tier in [0.0, 1.0]
 * @param severityProbHigh model probability for high severity tier in [0.0, 1.0]
 * @param areaRatio distress proposal area relative to full frame area [0.0, 1.0]
 * @param aspectRatio aspect ratio of distress proposal bounding box (>= 1.0)
 * @param priorityScore synthesized maintenance priority score [0, 100]
 * @param level categorized severity urgency tier
 * @param explanation human-readable breakdown of scoring factors
 */
public record DamageAssessment(
        Detection detection,
        float severityProbLow,
        float severityProbMedium,
        float severityProbHigh,
        float areaRatio,
        float aspectRatio,
        int priorityScore,
        SeverityLevel level,
        String explanation) {

    /**
     * Compact constructor validating component bounds and integrity.
     *
     * @throws NullPointerException if detection, level, or explanation is null
     * @throws IllegalArgumentException if probabilities outside [0, 1] or do not sum to 1 within 1e-3,
     *                                  priorityScore is outside [0, 100], or explanation is blank
     */
    public DamageAssessment {
        Objects.requireNonNull(detection, "detection must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");

        if (explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }

        if (severityProbLow < 0.0f || severityProbLow > 1.0f) {
            throw new IllegalArgumentException("severityProbLow must be in [0, 1]: " + severityProbLow);
        }
        if (severityProbMedium < 0.0f || severityProbMedium > 1.0f) {
            throw new IllegalArgumentException("severityProbMedium must be in [0, 1]: " + severityProbMedium);
        }
        if (severityProbHigh < 0.0f || severityProbHigh > 1.0f) {
            throw new IllegalArgumentException("severityProbHigh must be in [0, 1]: " + severityProbHigh);
        }

        float probSum = severityProbLow + severityProbMedium + severityProbHigh;
        if (Math.abs(probSum - 1.0f) > 1e-3f) {
            throw new IllegalArgumentException("Severity probabilities must sum within 1e-3 of 1.0, got: " + probSum);
        }

        if (priorityScore < 0 || priorityScore > 100) {
            throw new IllegalArgumentException("priorityScore must be in [0, 100]: " + priorityScore);
        }

        if (areaRatio < 0.0f) {
            throw new IllegalArgumentException("areaRatio must be non-negative: " + areaRatio);
        }
        if (aspectRatio < 0.0f) {
            throw new IllegalArgumentException("aspectRatio must be non-negative: " + aspectRatio);
        }
    }
}
