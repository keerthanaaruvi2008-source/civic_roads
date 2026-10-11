package com.roaddamage.infer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reusable memory pool for direct, native-order {@link FloatBuffer} instances, reducing garbage collection pressure
 * and eliminating JNI buffer re-allocations on the inference hot path.
 */
public final class DirectFloatBufferPool implements AutoCloseable {

    private static final int DEFAULT_MAX_CAPACITY = 32;

    private final int maxCapacity;
    private final ConcurrentLinkedDeque<FloatBuffer> pool;
    private final Set<FloatBuffer> allocatedBuffers;
    private final Set<FloatBuffer> inUseBuffers;
    private final AtomicLong allocationCount;
    private final AtomicBoolean closed;

    /**
     * Constructs a DirectFloatBufferPool with default maximum capacity.
     */
    public DirectFloatBufferPool() {
        this(DEFAULT_MAX_CAPACITY);
    }

    /**
     * Constructs a DirectFloatBufferPool with the specified maximum pooled buffer capacity.
     *
     * @param maxCapacity maximum number of idle direct buffers retained in the pool
     * @throws IllegalArgumentException if maxCapacity is less than 1
     */
    public DirectFloatBufferPool(int maxCapacity) {
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("maxCapacity must be >= 1: " + maxCapacity);
        }
        this.maxCapacity = maxCapacity;
        this.pool = new ConcurrentLinkedDeque<>();
        this.allocatedBuffers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        this.inUseBuffers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        this.allocationCount = new AtomicLong(0);
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Acquires a direct, native-order {@link FloatBuffer} with at least the requested float capacity.
     * The returned buffer is cleared and its limit set to {@code floatCount}.
     *
     * @param floatCount required number of float elements
     * @return an acquired FloatBuffer ready for population
     * @throws IllegalArgumentException if floatCount is not positive
     * @throws IllegalStateException if this pool is closed
     */
    public FloatBuffer acquire(int floatCount) {
        if (floatCount <= 0) {
            throw new IllegalArgumentException("floatCount must be > 0: " + floatCount);
        }
        if (closed.get()) {
            throw new IllegalStateException("DirectFloatBufferPool is closed");
        }

        FloatBuffer candidate = null;
        Iterator<FloatBuffer> it = pool.iterator();
        while (it.hasNext()) {
            FloatBuffer b = it.next();
            if (b.capacity() >= floatCount) {
                it.remove();
                candidate = b;
                break;
            }
        }

        if (candidate != null) {
            candidate.clear();
            candidate.limit(floatCount);
            inUseBuffers.add(candidate);
            return candidate;
        }

        ByteBuffer directBytes = ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder());
        FloatBuffer newBuf = directBytes.asFloatBuffer();
        allocatedBuffers.add(newBuf);
        inUseBuffers.add(newBuf);
        allocationCount.incrementAndGet();
        return newBuf;
    }

    /**
     * Releases a previously acquired {@link FloatBuffer} back to the pool for reuse.
     *
     * @param buf the FloatBuffer to return
     * @throws NullPointerException if buf is null
     * @throws IllegalArgumentException if buf was not created by this pool
     * @throws IllegalStateException if buf is not currently checked out
     */
    public void release(FloatBuffer buf) {
        Objects.requireNonNull(buf, "buf must not be null");
        if (!allocatedBuffers.contains(buf)) {
            throw new IllegalArgumentException("Buffer was not created by this pool");
        }
        if (!inUseBuffers.remove(buf)) {
            throw new IllegalStateException("Buffer is not currently acquired or was already released");
        }
        if (!closed.get() && pool.size() < maxCapacity) {
            pool.offer(buf);
        }
    }

    /**
     * Closes the pool, dropping all idle and tracked buffer references so native memory can be garbage-collected.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pool.clear();
            allocatedBuffers.clear();
            inUseBuffers.clear();
        }
    }

    /**
     * Returns the total count of direct buffer allocations performed by this pool.
     *
     * @return allocation count
     */
    public long allocationCount() {
        return allocationCount.get();
    }

    /**
     * Returns the number of currently idle buffers cached in the pool.
     *
     * @return idle buffer count
     */
    public int idleCount() {
        return pool.size();
    }
}
