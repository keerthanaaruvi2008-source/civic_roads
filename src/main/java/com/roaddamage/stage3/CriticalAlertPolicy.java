package com.roaddamage.stage3;

import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.SeverityLevel;
import java.util.Objects;

/**
 * Pure policy rules defining when a detected road surface distress warrants critical immediate alerts.
 */
public final class CriticalAlertPolicy {

    private CriticalAlertPolicy() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Determines whether an assessed road damage distress proposal satisfies criteria for an emergency alert.
     * <p>
     * An alert is triggered if:
     * <ul>
     *   <li>The assessment level is {@link SeverityLevel#HIGH} or {@link SeverityLevel#CRITICAL}, OR</li>
     *   <li>The damage is a {@link DamageClass#D40_POTHOLE} and its priority score is &gt;= 60.</li>
     * </ul>
     *
     * @param assessment the damage assessment record
     * @return true if an alert must be dispatched, false otherwise
     * @throws NullPointerException if assessment is null
     */
    public static boolean isAlert(DamageAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment must not be null");

        SeverityLevel level = assessment.level();
        boolean isHighOrCritical = (level == SeverityLevel.HIGH || level == SeverityLevel.CRITICAL);
        boolean isSeverePothole = (assessment.detection().damageClass() == DamageClass.D40_POTHOLE
                && assessment.priorityScore() >= ModelConfig.SEVERITY_ALERT_POTHOLE_SCORE_THRESHOLD);

        return isHighOrCritical || isSeverePothole;
    }
}
