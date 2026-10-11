package com.roaddamage.core.model;

import java.util.Objects;

/**
 * Immutable detected road surface damage proposal with localized bounding box, class, and confidence score.
 *
 * @param box localized coordinates in the source image space
 * @param damageClass identified damage classification
 * @param confidence detector probability in [0.0, 1.0]
 */
public record Detection(BoundingBox box, DamageClass damageClass, float confidence) {

    /**
     * Compact constructor validating bounding box, damage class, and confidence score range.
     *
     * @throws NullPointerException if box or damageClass is null
     * @throws IllegalArgumentException if confidence is not between 0.0 and 1.0
     */
    public Detection {
        Objects.requireNonNull(box, "box must not be null");
        Objects.requireNonNull(damageClass, "damageClass must not be null");
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be in [0, 1]: " + confidence);
        }
    }
}
