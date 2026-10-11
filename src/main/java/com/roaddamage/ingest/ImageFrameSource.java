package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import com.roaddamage.core.model.FrameEnvelope;
import com.roaddamage.core.model.ManagedMat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * Single-image frame source producing exactly one frame envelope from a static image file.
 */
public final class ImageFrameSource implements FrameSource {

    private final Path path;
    private final FrameEnvelope envelope;
    private final AtomicBoolean consumed;
    private final AtomicBoolean closed;

    /**
     * Constructs an ImageFrameSource by decoding the image at the given path.
     *
     * @param path the path to the image file
     * @throws IngestionException if the image cannot be read or decoded
     */
    public ImageFrameSource(Path path) throws IngestionException {
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.consumed = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);

        // Justification: Native image decoded via imread; ownership is transferred to ManagedMat in envelope,
        // which is closed either by caller upon consumption or by this source's close() if unconsumed.
        Mat mat = Imgcodecs.imread(path.toString(), Imgcodecs.IMREAD_COLOR);
        if (mat == null || mat.empty()) {
            if (mat != null) {
                mat.release();
            }
            throw new IngestionException("ingest", "Failed to decode image: " + path, null,
                    Map.of("path", path.toString(), "error", "imread returned empty"));
        }

        ManagedMat managedMat = new ManagedMat(mat);
        this.envelope = new FrameEnvelope(0L, 0L, managedMat, path.toString());
    }

    @Override
    public Optional<FrameEnvelope> next() throws IngestionException {
        if (closed.get()) {
            return Optional.empty();
        }
        if (consumed.compareAndSet(false, true)) {
            return Optional.of(envelope);
        }
        return Optional.empty();
    }

    @Override
    public double fps() {
        return 0.0;
    }

    @Override
    public int totalFrames() {
        return 1;
    }

    @Override
    public String sourceId() {
        return path.toString();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (consumed.compareAndSet(false, true)) {
                envelope.close();
            }
        }
    }
}
