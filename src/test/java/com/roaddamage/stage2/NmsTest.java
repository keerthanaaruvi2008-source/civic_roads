package com.roaddamage.stage2;

import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NmsTest {

    @Test
    void emptyListReturnsEmpty() {
        List<Detection> res = Nms.classAwareNms(Collections.emptyList(), 0.5f);
        assertTrue(res.isEmpty());
    }

    @Test
    void suppressesOverlappingSameClassCandidates() {
        BoundingBox boxA = new BoundingBox(10.0f, 10.0f, 50.0f, 50.0f);
        BoundingBox boxB = new BoundingBox(12.0f, 12.0f, 52.0f, 52.0f); // High IoU with boxA

        Detection dHigh = new Detection(boxA, DamageClass.D40_POTHOLE, 0.90f);
        Detection dLow = new Detection(boxB, DamageClass.D40_POTHOLE, 0.70f);

        List<Detection> retained = Nms.classAwareNms(List.of(dLow, dHigh), 0.45f);
        assertEquals(1, retained.size());
        assertEquals(0.90f, retained.get(0).confidence());
    }

    @Test
    void retainsOverlappingDifferentClassCandidates() {
        BoundingBox boxA = new BoundingBox(10.0f, 10.0f, 50.0f, 50.0f);
        BoundingBox boxB = new BoundingBox(12.0f, 12.0f, 52.0f, 52.0f);

        Detection dClass1 = new Detection(boxA, DamageClass.D40_POTHOLE, 0.85f);
        Detection dClass2 = new Detection(boxB, DamageClass.D20_ALLIGATOR, 0.80f);

        List<Detection> retained = Nms.classAwareNms(List.of(dClass1, dClass2), 0.45f);
        assertEquals(2, retained.size(), "Class-aware NMS must retain overlapping boxes of different classes");
        assertEquals(0.85f, retained.get(0).confidence());
        assertEquals(0.80f, retained.get(1).confidence());
    }

    @Test
    void retainsNonOverlappingSameClassCandidates() {
        BoundingBox boxA = new BoundingBox(0.0f, 0.0f, 20.0f, 20.0f);
        BoundingBox boxB = new BoundingBox(100.0f, 100.0f, 120.0f, 120.0f);

        Detection dA = new Detection(boxA, DamageClass.D00_LONGITUDINAL, 0.60f);
        Detection dB = new Detection(boxB, DamageClass.D00_LONGITUDINAL, 0.75f);

        List<Detection> retained = Nms.classAwareNms(List.of(dA, dB), 0.45f);
        assertEquals(2, retained.size());
        assertEquals(0.75f, retained.get(0).confidence(), "Must be sorted by confidence descending");
        assertEquals(0.60f, retained.get(1).confidence());
    }
}
