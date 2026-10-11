package com.roaddamage.core.model;

import java.util.Objects;

/**
 * Immutable carrier record for a video or image frame and its metadata.
 *
 * @param frameIndex zero-based sequential frame index (or -1 for sentinel envelopes)
 * @param timestampMillis presentation timestamp in milliseconds
 * @param frame reference-counted ManagedMat wrapping the frame image
 * @param sourceId identifier of the media source providing the frame
 */
public record FrameEnvelope(long frameIndex, long timestampMillis, ManagedMat frame, String sourceId)
        implements AutoCloseable {

    /**
     * Compact constructor validating frame envelope attributes.
     *
     * @throws IllegalArgumentException if frameIndex is less than -1
     * @throws NullPointerException if frame or sourceId is null
     */
    public FrameEnvelope {
        if (frameIndex < -1) {
            throw new IllegalArgumentException("frameIndex must be >= 0 (or -1 for sentinel): " + frameIndex);
        }
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }

    /**
     * Indicates whether this envelope represents a pipeline termination sentinel.
     *
     * @return true if frameIndex is -1
     */
    public boolean isSentinel() {
        return frameIndex == -1;
    }

    /**
     * Closes the underlying ManagedMat, decrementing its reference count.
     */
    @Override
    public void close() {
        frame.close();
    }
}
