package com.roaddamage.stage3;

import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import com.roaddamage.core.model.SeverityLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CriticalAlertPolicyTest {

    private static DamageAssessment createAssessment(DamageClass damageClass, int score, SeverityLevel level) {
        Detection detection = new Detection(new BoundingBox(10.0f, 10.0f, 100.0f, 100.0f), damageClass, 0.85f);
        return new DamageAssessment(
                detection,
                0.1f, 0.3f, 0.6f,
                0.02f, 1.0f,
                score,
                level,
                "Test explanation"
        );
    }

    @Test
    void rejectsNullAssessment() {
        assertThrows(NullPointerException.class, () -> CriticalAlertPolicy.isAlert(null));
    }

    @Test
    void triggersOnHighOrCriticalSeverity() {
        DamageAssessment highAssessment = createAssessment(DamageClass.D00_LONGITUDINAL, 65, SeverityLevel.HIGH);
        assertTrue(CriticalAlertPolicy.isAlert(highAssessment));

        DamageAssessment criticalAssessment = createAssessment(DamageClass.D10_TRANSVERSE, 85, SeverityLevel.CRITICAL);
        assertTrue(CriticalAlertPolicy.isAlert(criticalAssessment));
    }

    @Test
    void triggersOnPotholeScoreThreshold() {
        // Pothole with score >= 60 (even if level would be evaluated at 60 as HIGH, we test both boundary and level)
        DamageAssessment pothole60 = createAssessment(DamageClass.D40_POTHOLE, 60, SeverityLevel.HIGH);
        assertTrue(CriticalAlertPolicy.isAlert(pothole60));

        // Pothole with score < 60 and MEDIUM level
        DamageAssessment pothole55 = createAssessment(DamageClass.D40_POTHOLE, 45, SeverityLevel.MEDIUM);
        assertFalse(CriticalAlertPolicy.isAlert(pothole55));
    }

    @Test
    void doesNotTriggerOnLowOrMediumNonPothole() {
        DamageAssessment lowCrack = createAssessment(DamageClass.D00_LONGITUDINAL, 20, SeverityLevel.LOW);
        assertFalse(CriticalAlertPolicy.isAlert(lowCrack));

        DamageAssessment medCrack = createAssessment(DamageClass.D20_ALLIGATOR, 45, SeverityLevel.MEDIUM);
        assertFalse(CriticalAlertPolicy.isAlert(medCrack));
    }
}
