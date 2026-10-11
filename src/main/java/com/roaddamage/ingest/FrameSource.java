package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import com.roaddamage.core.model.FrameEnvelope;
import java.util.Optional;

/**
 * Sequential source of video and image frame envelopes.
 */
public interface FrameSource extends AutoCloseable {

    /**
     * Retrieves the next available frame envelope.
     *
     * @return an {@link Optional} containing the next frame envelope, or {@link Optional#empty()} when exhausted
     * @throws IngestionException if reading or decoding the frame fails
     */
    Optional<FrameEnvelope> next() throws IngestionException;

    /**
     * Returns the frame rate of the media source.
     *
     * @return frames per second (0.0 for still images)
     */
    double fps();

    /**
     * Returns the total number of frames in the source if known.
     *
     * @return total frame count, or -1 if unknown
     */
    int totalFrames();

    /**
     * Returns the unique identifier or file path of the source.
     *
     * @return source identifier
     */
    String sourceId();

    /**
     * Opens a FrameSource suitable for the specified media file path.
     *
     * @param path media file path
     * @return FrameSource instance
     * @throws IngestionException if file format is unsupported or cannot be opened
     */
    static FrameSource open(java.nio.file.Path path) throws IngestionException {
        return FrameSourceFactory.open(path);
    }

    /**
     * Closes the frame source and releases all native decoders and file handles.
     */
    @Override
    void close();
}
