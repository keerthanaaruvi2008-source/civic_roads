package com.roaddamage.infer;

import ai.onnxruntime.OnnxTensor;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lifecycle lease wrapper for an {@link OnnxTensor} ensuring deterministic release of native memory.
 */
public final class TensorLease implements AutoCloseable {

    /** Global counter tracking the number of currently active, unclosed tensor leases. */
    public static final AtomicLong OPEN_LEASES = new AtomicLong(0);

    private final OnnxTensor tensor;
    private final AtomicBoolean closed;

    private TensorLease(OnnxTensor tensor) {
        this.tensor = Objects.requireNonNull(tensor, "tensor must not be null");
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Wraps an {@link OnnxTensor} in a new lease and increments the open leases counter.
     *
     * @param t the ONNX tensor to wrap
     * @return a new TensorLease instance
     * @throws NullPointerException if t is null
     */
    public static TensorLease wrap(OnnxTensor t) {
        TensorLease lease = new TensorLease(t);
        OPEN_LEASES.incrementAndGet();
        return lease;
    }

    /**
     * Returns the underlying ONNX tensor.
     *
     * @return the active OnnxTensor
     * @throws IllegalStateException if this lease has already been closed
     */
    public OnnxTensor tensor() {
        if (closed.get()) {
            throw new IllegalStateException("TensorLease is already closed");
        }
        return tensor;
    }

    /**
     * Closes the underlying ONNX tensor and decrements the open leases counter.
     * Subsequent calls to this method are idempotent.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            OPEN_LEASES.decrementAndGet();
            tensor.close();
        }
    }

    /**
     * Returns whether this lease has been closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }
}
