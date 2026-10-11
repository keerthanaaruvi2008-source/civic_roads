package com.roaddamage.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DetectionTest {

    @Test
    void validDetectionStoresComponents() {
        BoundingBox box = new BoundingBox(10.0f, 20.0f, 30.0f, 40.0f);
        Detection d = new Detection(box, DamageClass.D40_POTHOLE, 0.85f);
        assertEquals(box, d.box());
        assertEquals(DamageClass.D40_POTHOLE, d.damageClass());
        assertEquals(0.85f, d.confidence());
    }

    @Test
    void rejectsInvalidArguments() {
        BoundingBox box = new BoundingBox(0.0f, 0.0f, 10.0f, 10.0f);
        assertThrows(NullPointerException.class, () -> new Detection(null, DamageClass.D00_LONGITUDINAL, 0.5f));
        assertThrows(NullPointerException.class, () -> new Detection(box, null, 0.5f));
        assertThrows(IllegalArgumentException.class, () -> new Detection(box, DamageClass.D10_TRANSVERSE, -0.1f));
        assertThrows(IllegalArgumentException.class, () -> new Detection(box, DamageClass.D10_TRANSVERSE, 1.1f));
    }
}
