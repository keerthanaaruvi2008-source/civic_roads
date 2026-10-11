package com.roaddamage.stage3;

import com.roaddamage.core.model.DamageClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PriorityScorerTest {

    @Test
    void verifiesClassWeights() {
        assertEquals(0.55f, PriorityScorer.classWeight(DamageClass.D00_LONGITUDINAL));
        assertEquals(0.55f, PriorityScorer.classWeight(DamageClass.D10_TRANSVERSE));
        assertEquals(0.75f, PriorityScorer.classWeight(DamageClass.D20_ALLIGATOR));
        assertEquals(1.00f, PriorityScorer.classWeight(DamageClass.D40_POTHOLE));
    }

    @Test
    void calculatesDeterministicScores() {
        // Maximum severity pothole
        int maxPothole = PriorityScorer.score(DamageClass.D40_POTHOLE, 0.05f, 1.0f, 0.0f, 0.0f, 1.0f);
        assertEquals(100, maxPothole);

        // Minor longitudinal crack without aspect boost
        int minorCrack = PriorityScorer.score(DamageClass.D00_LONGITUDINAL, 0.005f, 2.0f, 1.0f, 0.0f, 0.0f);
        assertEquals(25, minorCrack);

        // Elongated transverse crack with aspect boost (>= 4.0)
        int elongatedCrack = PriorityScorer.score(DamageClass.D10_TRANSVERSE, 0.025f, 5.0f, 0.0f, 1.0f, 0.0f);
        assertEquals(62, elongatedCrack);

        // Pothole with high aspect ratio does NOT receive crack aspect boost
        int elongatedPothole = PriorityScorer.score(DamageClass.D40_POTHOLE, 0.025f, 5.0f, 0.0f, 1.0f, 0.0f);
        // Base = 100 * (0.40 * 1.0 + 0.30 * 0.5 + 0.30 * 0.5) = 100 * 0.70 = 70 (no aspect boost)
        assertEquals(70, elongatedPothole);
    }

    @Test
    void generatesExplanatorySentence() {
        String explanation = PriorityScorer.explain(DamageClass.D20_ALLIGATOR, 0.03f, 4.5f, 0.1f, 0.3f, 0.6f);
        assertTrue(explanation.contains("Priority score"));
        assertTrue(explanation.contains("Alligator crack"));
        assertTrue(explanation.contains("aspect boost"));
    }

    @Test
    void determinismAcrossRuns() {
        int expectedScore1 = PriorityScorer.score(DamageClass.D40_POTHOLE, 0.05f, 1.0f, 0.0f, 0.0f, 1.0f);
        int expectedScore2 = PriorityScorer.score(DamageClass.D00_LONGITUDINAL, 0.005f, 2.0f, 1.0f, 0.0f, 0.0f);
        int expectedScore3 = PriorityScorer.score(DamageClass.D10_TRANSVERSE, 0.025f, 5.0f, 0.0f, 1.0f, 0.0f);
        int expectedScore4 = PriorityScorer.score(DamageClass.D20_ALLIGATOR, 0.03f, 4.5f, 0.1f, 0.3f, 0.6f);

        for (int i = 0; i < 1000; i++) {
            assertEquals(expectedScore1, PriorityScorer.score(DamageClass.D40_POTHOLE, 0.05f, 1.0f, 0.0f, 0.0f, 1.0f));
            assertEquals(expectedScore2, PriorityScorer.score(DamageClass.D00_LONGITUDINAL, 0.005f, 2.0f, 1.0f, 0.0f, 0.0f));
            assertEquals(expectedScore3, PriorityScorer.score(DamageClass.D10_TRANSVERSE, 0.025f, 5.0f, 0.0f, 1.0f, 0.0f));
            assertEquals(expectedScore4, PriorityScorer.score(DamageClass.D20_ALLIGATOR, 0.03f, 4.5f, 0.1f, 0.3f, 0.6f));
        }
    }

    @Test
    void sampleScoresVerification() {
        // Sample 1: Negligible longitudinal crack
        int s1 = PriorityScorer.score(DamageClass.D00_LONGITUDINAL, 0.001f, 1.5f, 1.0f, 0.0f, 0.0f);
        assertEquals(23, s1);

        // Sample 2: Moderate transverse crack
        int s2 = PriorityScorer.score(DamageClass.D10_TRANSVERSE, 0.015f, 3.0f, 0.2f, 0.7f, 0.1f);
        assertEquals(45, s2);

        // Sample 3: Elongated transverse crack with aspect boost
        int s3 = PriorityScorer.score(DamageClass.D10_TRANSVERSE, 0.025f, 5.2f, 0.05f, 0.75f, 0.20f);
        assertEquals(64, s3);

        // Sample 4: Minor pothole below critical threshold
        int s4 = PriorityScorer.score(DamageClass.D40_POTHOLE, 0.005f, 1.2f, 1.0f, 0.0f, 0.0f);
        assertEquals(43, s4);

        // Sample 5: Large structural alligator crack grid
        int s5 = PriorityScorer.score(DamageClass.D20_ALLIGATOR, 0.045f, 1.8f, 0.05f, 0.25f, 0.70f);
        assertEquals(82, s5);

        // Sample 6: Saturated severe pothole
        int s6 = PriorityScorer.score(DamageClass.D40_POTHOLE, 0.060f, 1.1f, 0.0f, 0.10f, 0.90f);
        assertEquals(99, s6);
    }
}
