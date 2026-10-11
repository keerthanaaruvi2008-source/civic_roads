package com.roaddamage.stage1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage1ResultTest {

    @Test
    void validProbabilitiesAndTimingSucceed() {
        MobileNetV3Screener.Stage1Result res = new MobileNetV3Screener.Stage1Result(0.85f, 0.15f, true, 12_500_000L);
        assertEquals(0.85f, res.damageProbability());
        assertEquals(0.15f, res.cleanProbability());
        assertTrue(res.damaged());
        assertEquals(12_500_000L, res.inferenceNanos());

        MobileNetV3Screener.Stage1Result resClean = new MobileNetV3Screener.Stage1Result(0.20f, 0.80f, false, 5_000_000L);
        assertFalse(resClean.damaged());
    }

    @Test
    void rejectsProbabilitiesOutsideRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobileNetV3Screener.Stage1Result(-0.1f, 1.1f, false, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> new MobileNetV3Screener.Stage1Result(1.2f, -0.2f, true, 100L));
    }

    @Test
    void rejectsProbabilitiesNotSummingToOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobileNetV3Screener.Stage1Result(0.70f, 0.20f, true, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> new MobileNetV3Screener.Stage1Result(0.60f, 0.50f, true, 100L));
    }

    @Test
    void rejectsNegativeInferenceNanos() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobileNetV3Screener.Stage1Result(0.5f, 0.5f, true, -1L));
    }
}
