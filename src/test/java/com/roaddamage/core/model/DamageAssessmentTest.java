package com.roaddamage.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DamageAssessmentTest {

    private Detection sampleDetection() {
        return new Detection(new BoundingBox(10.0f, 20.0f, 60.0f, 80.0f), DamageClass.D40_POTHOLE, 0.90f);
    }

    @Test
    void validAssessmentConstructsProperly() {
        Detection d = sampleDetection();
        DamageAssessment da = new DamageAssessment(d, 0.10f, 0.30f, 0.60f, 0.02f, 1.5f, 68,
                SeverityLevel.HIGH, "High priority pothole");

        assertEquals(d, da.detection());
        assertEquals(0.10f, da.severityProbLow());
        assertEquals(0.30f, da.severityProbMedium());
        assertEquals(0.60f, da.severityProbHigh());
        assertEquals(0.02f, da.areaRatio());
        assertEquals(1.5f, da.aspectRatio());
        assertEquals(68, da.priorityScore());
        assertEquals(SeverityLevel.HIGH, da.level());
        assertEquals("High priority pothole", da.explanation());
    }

    @Test
    void rejectsInvalidProbabilitiesAndScores() {
        Detection d = sampleDetection();
        // Probabilities don't sum to 1
        assertThrows(IllegalArgumentException.class,
                () -> new DamageAssessment(d, 0.1f, 0.1f, 0.1f, 0.02f, 1.0f, 50, SeverityLevel.HIGH, "test"));

        // Out-of-bounds probability
        assertThrows(IllegalArgumentException.class,
                () -> new DamageAssessment(d, -0.1f, 0.5f, 0.6f, 0.02f, 1.0f, 50, SeverityLevel.HIGH, "test"));

        // Priority score out of range
        assertThrows(IllegalArgumentException.class,
                () -> new DamageAssessment(d, 0.2f, 0.3f, 0.5f, 0.02f, 1.0f, 105, SeverityLevel.CRITICAL, "test"));

        // Blank explanation
        assertThrows(IllegalArgumentException.class,
                () -> new DamageAssessment(d, 0.2f, 0.3f, 0.5f, 0.02f, 1.0f, 50, SeverityLevel.HIGH, "   "));
    }
}
