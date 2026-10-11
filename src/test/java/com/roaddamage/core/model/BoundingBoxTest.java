package com.roaddamage.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BoundingBoxTest {

    @Test
    void dimensionsAndAreaComputedCorrectly() {
        BoundingBox box = new BoundingBox(10.0f, 20.0f, 50.0f, 80.0f);
        assertEquals(40.0f, box.width());
        assertEquals(60.0f, box.height());
        assertEquals(2400.0f, box.area());
    }

    @Test
    void rejectsInvertedCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(50.0f, 10.0f, 20.0f, 30.0f));
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(10.0f, 50.0f, 20.0f, 30.0f));
    }

    @Test
    void iouCalculations() {
        BoundingBox box1 = new BoundingBox(0.0f, 0.0f, 10.0f, 10.0f); // area = 100
        BoundingBox box2 = new BoundingBox(0.0f, 0.0f, 10.0f, 10.0f); // identical
        assertEquals(1.0f, box1.iou(box2), 1e-6f);

        BoundingBox box3 = new BoundingBox(10.0f, 10.0f, 20.0f, 20.0f); // disjoint
        assertEquals(0.0f, box1.iou(box3), 1e-6f);

        BoundingBox box4 = new BoundingBox(5.0f, 0.0f, 15.0f, 10.0f); // 50% overlap (inter=50, union=150)
        assertEquals(50.0f / 150.0f, box1.iou(box4), 1e-6f);

        BoundingBox zeroArea1 = new BoundingBox(5.0f, 5.0f, 5.0f, 5.0f);
        BoundingBox zeroArea2 = new BoundingBox(5.0f, 5.0f, 5.0f, 5.0f);
        assertEquals(0.0f, zeroArea1.iou(zeroArea2));
    }

    @Test
    void clipToBounds() {
        BoundingBox box = new BoundingBox(-10.0f, -5.0f, 150.0f, 250.0f);
        BoundingBox clipped = box.clipTo(100, 200);

        assertEquals(0.0f, clipped.x1());
        assertEquals(0.0f, clipped.y1());
        assertEquals(100.0f, clipped.x2());
        assertEquals(200.0f, clipped.y2());
    }
}
