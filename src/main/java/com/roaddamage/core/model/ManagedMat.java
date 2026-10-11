package com.roaddamage.core.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.opencv.core.Mat;

/**
 * Reference-counted lifecycle wrapper for an OpenCV {@link Mat} ensuring deterministic native resource release.
 */
public final class ManagedMat implements AutoCloseable {

    private static final AtomicLong ACTIVE_INSTANCES = new AtomicLong(0);

    private final Mat mat;
    private final AtomicInteger refCount;

    /**
     * Constructs a ManagedMat wrapping the given native Mat with an initial reference count of 1.
     *
     * @param mat the underlying OpenCV Mat
     * @throws NullPointerException if mat is null
     */
    public ManagedMat(Mat mat) {
        this.mat = Objects.requireNonNull(mat, "mat must not be null");
        this.refCount = new AtomicInteger(1);
        ACTIVE_INSTANCES.incrementAndGet();
    }

    /**
     * Increments the reference count and returns this instance.
     *
     * @return this ManagedMat instance
     * @throws IllegalStateException if this ManagedMat has already been released
     */
    public ManagedMat retain() {
        while (true) {
            int current = refCount.get();
            if (current <= 0) {
                throw new IllegalStateException("Cannot retain ManagedMat: already released");
            }
            if (refCount.compareAndSet(current, current + 1)) {
                return this;
            }
        }
    }

    /**
     * Decrements the reference count and releases the native Mat when the count reaches zero.
     * Calls to this method after the Mat has been released are idempotent.
     */
    @Override
    public void close() {
        while (true) {
            int current = refCount.get();
            if (current <= 0) {
                return;
            }
            if (refCount.compareAndSet(current, current - 1)) {
                if (current - 1 == 0) {
                    mat.release();
                    ACTIVE_INSTANCES.decrementAndGet();
                }
                return;
            }
        }
    }

    /**
     * Returns the number of currently active (unreleased) {@link ManagedMat} instances.
     *
     * @return current active count
     */
    public static long activeCount() {
        return ACTIVE_INSTANCES.get();
    }

    /**
     * Returns the underlying native OpenCV Mat.
     *
     * @return the Mat
     * @throws IllegalStateException if this ManagedMat has already been released
     */
    public Mat mat() {
        if (isReleased()) {
            throw new IllegalStateException("Cannot access Mat: ManagedMat is released");
        }
        return mat;
    }

    /**
     * Returns whether the underlying native Mat has been released.
     *
     * @return true if the reference count has reached zero and the Mat has been released, false otherwise
     */
    public boolean isReleased() {
        return refCount.get() <= 0;
    }
}
