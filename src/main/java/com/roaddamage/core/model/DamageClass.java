package com.roaddamage.core.model;

/**
 * Categorization taxonomy for road surface distress in the Japanese Road Damage Dataset (RDD) format.
 */
public enum DamageClass {

    /** Longitudinal crack running parallel to traffic direction. */
    D00_LONGITUDINAL(0, "Longitudinal crack"),

    /** Transverse crack running perpendicular to traffic direction. */
    D10_TRANSVERSE(1, "Transverse crack"),

    /** Fatigue alligator / crocodile cracking with interconnected patterns. */
    D20_ALLIGATOR(2, "Alligator crack"),

    /** Bowl-shaped depression or pothole in the pavement surface. */
    D40_POTHOLE(3, "Pothole");

    private final int index;
    private final String label;

    DamageClass(int index, String label) {
        this.index = index;
        this.label = label;
    }

    /**
     * Returns the numeric class index matching YOLO output tensor class channels.
     *
     * @return 0-based class index
     */
    public int index() {
        return index;
    }

    /**
     * Returns the human-readable description of the road damage type.
     *
     * @return label string
     */
    public String label() {
        return label;
    }

    /**
     * Resolves a {@link DamageClass} from its 0-based integer index.
     *
     * @param index the class index
     * @return matching DamageClass
     * @throws IllegalArgumentException if index does not correspond to any known DamageClass
     */
    public static DamageClass fromIndex(int index) {
        for (DamageClass dc : values()) {
            if (dc.index == index) {
                return dc;
            }
        }
        throw new IllegalArgumentException("Unknown DamageClass index: " + index);
    }
}
