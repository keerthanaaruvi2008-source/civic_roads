package com.roaddamage.ingest;

import com.roaddamage.core.model.FrameEnvelope;
import com.roaddamage.core.model.ManagedMat;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.opencv.core.Mat;

/**
 * Thread-safe bounded buffer queue for FrameEnvelopes preventing out-of-memory errors by dropping frames on overload.
 */
public final class BoundedFrameQueue {

    private final int capacity;
    private final ArrayBlockingQueue<FrameEnvelope> queue;
    private final AtomicLong droppedCount;

    /**
     * Constructs a BoundedFrameQueue with the specified maximum capacity.
     *
     * @param capacity maximum number of envelopes held in memory
     * @throws IllegalArgumentException if capacity is less than 1
     */
    public BoundedFrameQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1: " + capacity);
        }
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.droppedCount = new AtomicLong(0L);
    }

    /**
     * Offers a frame envelope to the queue, or drops and closes it if the queue is full.
     *
     * @param envelope the frame envelope to enqueue
     * @return true if accepted, false if dropped
     */
    public boolean offerOrDrop(FrameEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        boolean accepted = queue.offer(envelope);
        if (!accepted) {
            droppedCount.incrementAndGet();
            envelope.close();
        }
        return accepted;
    }

    /**
     * Retrieves and removes the head frame envelope of this queue, waiting if necessary until an element becomes available.
     *
     * @return the head frame envelope
     * @throws InterruptedException if interrupted while waiting
     */
    public FrameEnvelope take() throws InterruptedException {
        return queue.take();
    }

    /**
     * Enqueues a termination sentinel frame envelope with frameIndex = -1 to signal shutdown.
     */
    public void poison() {
        // Justification: Dummy Mat allocated for termination sentinel FrameEnvelope; closed by queue consumer upon reading -1 or on thread interrupt.
        ManagedMat dummyMat = new ManagedMat(new Mat());
        FrameEnvelope sentinel = new FrameEnvelope(-1L, 0L, dummyMat, "POISON_SENTINEL");
        try {
            queue.put(sentinel);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sentinel.close();
        }
    }

    /**
     * Returns the total number of frames dropped due to back-pressure queue overflow.
     *
     * @return dropped frame count
     */
    public long droppedCount() {
        return droppedCount.get();
    }

    /**
     * Returns the current number of frame envelopes held in the queue.
     *
     * @return current queue size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns the configured capacity of the queue.
     *
     * @return maximum capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Clears all remaining envelopes from the queue and closes each one to prevent resource leaks.
     */
    public void clearAndClose() {
        FrameEnvelope env;
        while ((env = queue.poll()) != null) {
            env.close();
        }
    }
}
