package com.roaddamage.ingest;

import com.roaddamage.core.model.FrameEnvelope;
import com.roaddamage.core.model.ManagedMat;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BoundedFrameQueueTest {

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    private static FrameEnvelope createEnvelope(long index) {
        return new FrameEnvelope(index, index * 100L, new ManagedMat(new Mat(4, 4, CvType.CV_8UC1)), "test");
    }

    @Test
    void dropsAndClosesEnvelopeWhenFull() {
        BoundedFrameQueue queue = new BoundedFrameQueue(2);
        FrameEnvelope e1 = createEnvelope(0);
        FrameEnvelope e2 = createEnvelope(1);
        FrameEnvelope e3 = createEnvelope(2);

        assertTrue(queue.offerOrDrop(e1));
        assertTrue(queue.offerOrDrop(e2));
        assertFalse(queue.offerOrDrop(e3));

        assertEquals(1L, queue.droppedCount());
        assertTrue(e3.frame().isReleased(), "Dropped envelope frame must be released");
        assertFalse(e1.frame().isReleased());
        assertFalse(e2.frame().isReleased());

        queue.clearAndClose();
        assertTrue(e1.frame().isReleased());
        assertTrue(e2.frame().isReleased());
    }

    @Test
    void poisonSendsSentinelEnvelope() throws Exception {
        BoundedFrameQueue queue = new BoundedFrameQueue(5);
        queue.poison();
        FrameEnvelope sentinel = queue.take();
        try {
            assertTrue(sentinel.isSentinel());
            assertEquals(-1L, sentinel.frameIndex());
        } finally {
            sentinel.close();
            assertTrue(sentinel.frame().isReleased());
        }
    }
}
