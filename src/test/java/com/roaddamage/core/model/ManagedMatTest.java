package com.roaddamage.core.model;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManagedMatTest {

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    @Test
    void refCountingReleasesOnlyOnLastClose() {
        ManagedMat m = new ManagedMat(new Mat(2, 2, CvType.CV_8UC1));
        m.retain();
        m.close();
        assertFalse(m.isReleased());
        m.close();
        assertTrue(m.isReleased());
        assertThrows(IllegalStateException.class, m::mat);
    }

    @Test
    void closeIsIdempotentAfterRelease() {
        ManagedMat m = new ManagedMat(new Mat(2, 2, CvType.CV_8UC1));
        m.close();
        assertTrue(m.isReleased());
        m.close();
        assertTrue(m.isReleased());
    }

    @Test
    void retainThrowsAfterRelease() {
        ManagedMat m = new ManagedMat(new Mat(2, 2, CvType.CV_8UC1));
        m.close();
        assertThrows(IllegalStateException.class, m::retain);
    }
}
