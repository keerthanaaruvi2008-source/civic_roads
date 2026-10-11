package com.roaddamage.core.model;

/**
 * Pavement distress maintenance severity and remediation urgency tiers.
 */
public enum SeverityLevel {

    /** Negligible or surface-level cosmetic wear (Priority 0–24). */
    LOW(0),

    /** Moderate distress requiring scheduled maintenance (Priority 25–49). */
    MEDIUM(1),

    /** Severe structural failure requiring prioritized intervention (Priority 50–74). */
    HIGH(2),

    /** Critical safety hazard requiring immediate emergency repair (Priority 75–100). */
    CRITICAL(3);

    private final int rank;

    SeverityLevel(int rank) {
        this.rank = rank;
    }

    /**
     * Returns numeric tier rank (0 for LOW to 3 for CRITICAL).
     *
     * @return ordinal severity rank
     */
    public int rank() {
        return rank;
    }

    /**
     * Maps an integer priority score in [0, 100] to its corresponding {@link SeverityLevel}.
     *
     * @param priorityScore priority score in range [0, 100]
     * @return resolved SeverityLevel
     * @throws IllegalArgumentException if priorityScore is outside [0, 100]
     */
    public static SeverityLevel fromScore(int priorityScore) {
        if (priorityScore < 0 || priorityScore > 100) {
            throw new IllegalArgumentException("priorityScore must be in [0, 100]: " + priorityScore);
        }
        if (priorityScore < 25) {
            return LOW;
        } else if (priorityScore < 50) {
            return MEDIUM;
        } else if (priorityScore < 75) {
            return HIGH;
        } else {
            return CRITICAL;
        }
    }
}
