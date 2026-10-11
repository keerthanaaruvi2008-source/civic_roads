package com.roaddamage.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DamageClassTest {

    @Test
    void resolvesAllDamageClassesByIndex() {
        assertEquals(DamageClass.D00_LONGITUDINAL, DamageClass.fromIndex(0));
        assertEquals(DamageClass.D10_TRANSVERSE, DamageClass.fromIndex(1));
        assertEquals(DamageClass.D20_ALLIGATOR, DamageClass.fromIndex(2));
        assertEquals(DamageClass.D40_POTHOLE, DamageClass.fromIndex(3));

        assertEquals("Longitudinal crack", DamageClass.D00_LONGITUDINAL.label());
        assertEquals("Transverse crack", DamageClass.D10_TRANSVERSE.label());
        assertEquals("Alligator crack", DamageClass.D20_ALLIGATOR.label());
        assertEquals("Pothole", DamageClass.D40_POTHOLE.label());
    }

    @Test
    void invalidIndexThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> DamageClass.fromIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> DamageClass.fromIndex(4));
    }
}
