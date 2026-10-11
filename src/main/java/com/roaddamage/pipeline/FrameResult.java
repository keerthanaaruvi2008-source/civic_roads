package com.roaddamage.pipeline;

import com.roaddamage.core.model.DamageAssessment;
import java.util.List;

/**
 * Unified diagnostic evaluation outcome for a single processed video or image frame.
 *
 * @param frameIndex sequence index of source frame
 * @param timestampMillis elapsed or capture timestamp in milliseconds
 * @param damaged true if Stage 1 determined the frame exhibits distress
 * @param stage1Damage predicted damage probability from Stage 1 screener
 * @param assessments unmodifiable collection of fine-grained localized distress assessments
 * @param stage1Nanos execution duration of Stage 1 screening in nanoseconds
 * @param stage2Nanos execution duration of Stage 2 detection in nanoseconds (0 if bypassed)
 * @param stage3Nanos execution duration of Stage 3 severity assessment in nanoseconds (0 if bypassed)
 */
public record FrameResult(
        long frameIndex,
        long timestampMillis,
        boolean damaged,
        float stage1Damage,
        List<DamageAssessment> assessments,
        long stage1Nanos,
        long stage2Nanos,
        long stage3Nanos) {

    /**
     * Compact constructor guaranteeing immutability of assessment items.
     */
    public FrameResult {
        assessments = (assessments == null) ? List.of() : List.copyOf(assessments);
    }
}
