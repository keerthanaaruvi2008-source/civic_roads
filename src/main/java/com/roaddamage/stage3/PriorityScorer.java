package com.roaddamage.stage3;

import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.model.DamageClass;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure mathematical scoring engine computing maintenance priority [0, 100] based on damage taxonomy,
 * physical distress footprint, neural severity estimation, and geometrical elongation.
 */
public final class PriorityScorer {

    private PriorityScorer() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Resolves the hazard weighting assigned to a specific damage taxonomy class.
     *
     * @param cls the damage classification
     * @return base class hazard weight in [0.0, 1.0]
     */
    public static float classWeight(DamageClass cls) {
        Objects.requireNonNull(cls, "DamageClass must not be null");
        return switch (cls) {
            case D00_LONGITUDINAL -> ModelConfig.SEVERITY_CLASS_WEIGHT_D00;
            case D10_TRANSVERSE -> ModelConfig.SEVERITY_CLASS_WEIGHT_D10;
            case D20_ALLIGATOR -> ModelConfig.SEVERITY_CLASS_WEIGHT_D20;
            case D40_POTHOLE -> ModelConfig.SEVERITY_CLASS_WEIGHT_D40;
        };
    }

    /**
     * Calculates the composite remediation priority score in [0, 100].
     *
     * @param cls damage taxonomy classification
     * @param areaRatio relative damage surface area [0.0, 1.0]
     * @param aspectRatio bounding box aspect ratio (length / width >= 1.0)
     * @param pLow predicted probability of low severity tier
     * @param pMed predicted probability of medium severity tier
     * @param pHigh predicted probability of high severity tier
     * @return clamped priority score between 0 and 100
     */
    public static int score(DamageClass cls, float areaRatio, float aspectRatio,
                            float pLow, float pMed, float pHigh) {
        float classTerm = classWeight(cls);
        float areaScore = Math.min(1.0f, Math.max(0.0f, areaRatio) / ModelConfig.SEVERITY_AREA_SATURATION_RATIO);
        float modelTerm = (0.0f * pLow + 0.5f * pMed + 1.0f * pHigh);

        boolean isCrack = (cls == DamageClass.D00_LONGITUDINAL
                || cls == DamageClass.D10_TRANSVERSE
                || cls == DamageClass.D20_ALLIGATOR);

        float aspectBoost = (isCrack && aspectRatio >= ModelConfig.SEVERITY_ASPECT_CRACK_THRESHOLD)
                ? ModelConfig.SEVERITY_ASPECT_BOOST : 0.0f;

        float raw = 100.0f * (ModelConfig.SEVERITY_WEIGHT_CLASS * classTerm
                + ModelConfig.SEVERITY_WEIGHT_AREA * areaScore
                + ModelConfig.SEVERITY_WEIGHT_MODEL * modelTerm)
                + 100.0f * aspectBoost;

        return Math.clamp(Math.round(raw), 0, 100);
    }

    /**
     * Generates a concise human-readable sentence explaining the contributing terms to the priority score.
     *
     * @param cls damage taxonomy classification
     * @param areaRatio relative damage surface area
     * @param aspectRatio bounding box aspect ratio
     * @param pLow predicted low severity probability
     * @param pMed predicted medium severity probability
     * @param pHigh predicted high severity probability
     * @return single explanation sentence detailing the score breakdown
     */
    public static String explain(DamageClass cls, float areaRatio, float aspectRatio,
                                 float pLow, float pMed, float pHigh) {
        int priority = score(cls, areaRatio, aspectRatio, pLow, pMed, pHigh);
        float classTerm = classWeight(cls);
        float areaScore = Math.min(1.0f, Math.max(0.0f, areaRatio) / ModelConfig.SEVERITY_AREA_SATURATION_RATIO);
        float modelTerm = (0.0f * pLow + 0.5f * pMed + 1.0f * pHigh);

        boolean isCrack = (cls == DamageClass.D00_LONGITUDINAL
                || cls == DamageClass.D10_TRANSVERSE
                || cls == DamageClass.D20_ALLIGATOR);
        boolean boostApplied = (isCrack && aspectRatio >= ModelConfig.SEVERITY_ASPECT_CRACK_THRESHOLD);

        String boostSuffix = boostApplied
                ? String.format(Locale.US, " with +%.0f%% aspect boost for elongated crack", ModelConfig.SEVERITY_ASPECT_BOOST * 100.0f)
                : "";

        return String.format(Locale.US,
                "Priority score %d derived from %s class weight (%.0f%%), area ratio (%.1f%% of saturation), and model severity (%.0f%%)%s.",
                priority, cls.label(), classTerm * 100.0f, areaScore * 100.0f, modelTerm * 100.0f, boostSuffix);
    }
}
