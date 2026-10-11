package com.roaddamage.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SeverityLevelTest {

    @Test
    void resolvesSeverityLevelsAcrossAllBands() {
        // LOW: 0 - 24
        assertEquals(SeverityLevel.LOW, SeverityLevel.fromScore(0));
        assertEquals(SeverityLevel.LOW, SeverityLevel.fromScore(12));
        assertEquals(SeverityLevel.LOW, SeverityLevel.fromScore(24));
        assertEquals(0, SeverityLevel.LOW.rank());

        // MEDIUM: 25 - 49
        assertEquals(SeverityLevel.MEDIUM, SeverityLevel.fromScore(25));
        assertEquals(SeverityLevel.MEDIUM, SeverityLevel.fromScore(37));
        assertEquals(SeverityLevel.MEDIUM, SeverityLevel.fromScore(49));
        assertEquals(1, SeverityLevel.MEDIUM.rank());

        // HIGH: 50 - 74
        assertEquals(SeverityLevel.HIGH, SeverityLevel.fromScore(50));
        assertEquals(SeverityLevel.HIGH, SeverityLevel.fromScore(60));
        assertEquals(SeverityLevel.HIGH, SeverityLevel.fromScore(74));
        assertEquals(2, SeverityLevel.HIGH.rank());

        // CRITICAL: 75 - 100
        assertEquals(SeverityLevel.CRITICAL, SeverityLevel.fromScore(75));
        assertEquals(SeverityLevel.CRITICAL, SeverityLevel.fromScore(85));
        assertEquals(SeverityLevel.CRITICAL, SeverityLevel.fromScore(100));
        assertEquals(3, SeverityLevel.CRITICAL.rank());
    }

    @Test
    void rejectsScoresOutsideValidRange() {
        assertThrows(IllegalArgumentException.class, () -> SeverityLevel.fromScore(-1));
        assertThrows(IllegalArgumentException.class, () -> SeverityLevel.fromScore(101));
    }
}
