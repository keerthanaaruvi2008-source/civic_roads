package com.roaddamage.infer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DirectFloatBufferPoolTest {

    @Test
    void acquiresDirectNativeOrderBufferAndReusesOnRelease() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(4)) {
            assertEquals(0L, pool.allocationCount());

            FloatBuffer buf1 = pool.acquire(100);
            assertTrue(buf1.isDirect(), "Acquired buffer must be direct");
            assertEquals(ByteOrder.nativeOrder(), buf1.order(), "Acquired buffer must use native byte order");
            assertEquals(100, buf1.limit());
            assertEquals(1L, pool.allocationCount(), "First acquire must increment allocationCount");

            pool.release(buf1);
            assertEquals(1, pool.idleCount());

            FloatBuffer buf2 = pool.acquire(100);
            assertSame(buf1, buf2, "Pool must reuse released buffer instance");
            assertEquals(1L, pool.allocationCount(), "Reusing buffer must not increment allocationCount");

            pool.release(buf2);
        }
    }

    @Test
    void rejectsAlienBufferOnRelease() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(4)) {
            FloatBuffer alien = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
            assertThrows(IllegalArgumentException.class, () -> pool.release(alien));
        }
    }

    @Test
    void rejectsDoubleRelease() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(4)) {
            FloatBuffer buf = pool.acquire(50);
            pool.release(buf);
            assertThrows(IllegalStateException.class, () -> pool.release(buf));
        }
    }

    @Test
    void invalidArgumentsAndClosedStateThrowExceptions() {
        try (DirectFloatBufferPool pool = new DirectFloatBufferPool(4)) {
            assertThrows(IllegalArgumentException.class, () -> pool.acquire(0));
            assertThrows(IllegalArgumentException.class, () -> pool.acquire(-5));
            assertThrows(NullPointerException.class, () -> pool.release(null));
        }

        DirectFloatBufferPool closedPool = new DirectFloatBufferPool(4);
        closedPool.close();
        assertThrows(IllegalStateException.class, () -> closedPool.acquire(10));
    }
}
