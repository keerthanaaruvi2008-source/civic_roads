package com.roaddamage.export;

import com.roaddamage.core.model.SeverityLevel;
import java.util.Objects;

/**
 * Tabular inspection damage entry suitable for analytical and audit CSV reporting.
 *
 * @param frameIndex sequence index of source video frame
 * @param timestampMillis elapsed or capture timestamp in milliseconds
 * @param damageClassLabel human-readable label of detected damage taxonomy
 * @param confidence detection model confidence in [0.0, 1.0]
 * @param x1 bounding box left coordinate
 * @param y1 bounding box top coordinate
 * @param x2 bounding box right coordinate
 * @param y2 bounding box bottom coordinate
 * @param areaRatio distress area relative to full frame area
 * @param aspectRatio bounding box aspect ratio
 * @param priorityScore synthesized remediation priority score [0, 100]
 * @param severityLevel assigned severity urgency tier
 * @param explanation diagnostic rationale behind scoring
 */
public record DamageRow(
        long frameIndex,
        long timestampMillis,
        String damageClassLabel,
        float confidence,
        float x1,
        float y1,
        float x2,
        float y2,
        float areaRatio,
        float aspectRatio,
        int priorityScore,
        SeverityLevel severityLevel,
        String explanation) {

    /**
     * Compact constructor validating field integrity.
     */
    public DamageRow {
        Objects.requireNonNull(damageClassLabel, "damageClassLabel must not be null");
        Objects.requireNonNull(severityLevel, "severityLevel must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
    }
}
