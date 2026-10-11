package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import com.roaddamage.core.model.FrameEnvelope;
import com.roaddamage.core.model.ManagedMat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/**
 * Sequential video frame grabber utilizing OpenCV VideoCapture with optional frame skipping.
 */
public final class VideoFrameGrabber implements FrameSource {

    private final String sourceId;
    private final int frameStride;
    private final VideoCapture cap;
    private final Mat scratchMat;
    private boolean closed;
    private long producedFrames;
    private boolean firstFrame;

    /**
     * Constructs a VideoFrameGrabber for the given video path with a frame stride of 1.
     *
     * @param path path to the video file
     * @throws IngestionException if the video cannot be opened
     */
    public VideoFrameGrabber(Path path) throws IngestionException {
        this(path, 1);
    }

    /**
     * Constructs a VideoFrameGrabber for the given video path and frame stride.
     *
     * @param path path to the video file
     * @param frameStride frame sampling stride (>= 1)
     * @throws IngestionException if the video cannot be opened
     */
    public VideoFrameGrabber(Path path, int frameStride) throws IngestionException {
        this(Objects.requireNonNull(path, "path must not be null").toString(), frameStride);
    }

    /**
     * Constructs a VideoFrameGrabber for the given video source string with a frame stride of 1.
     *
     * @param source source URI or file path
     * @throws IngestionException if the video cannot be opened
     */
    public VideoFrameGrabber(String source) throws IngestionException {
        this(source, 1);
    }

    /**
     * Constructs a VideoFrameGrabber for the given video source and frame stride.
     *
     * @param source source URI or file path
     * @param frameStride frame sampling stride (>= 1)
     * @throws IngestionException if the video cannot be opened
     */
    public VideoFrameGrabber(String source, int frameStride) throws IngestionException {
        this.sourceId = Objects.requireNonNull(source, "source must not be null");
        if (frameStride < 1) {
            throw new IllegalArgumentException("frameStride must be >= 1: " + frameStride);
        }
        this.frameStride = frameStride;
        // Justification: Reusable scratch buffer for native frame decoding; deterministically released in close() or upon failure.
        this.scratchMat = new Mat();
        // Justification: Native VideoCapture handle; released in close() or when opening fails.
        VideoCapture capture = new VideoCapture(source, Videoio.CAP_FFMPEG);
        if (!capture.isOpened()) {
            capture.release();
            capture = new VideoCapture(source, Videoio.CAP_ANY);
        }
        this.cap = capture;
        if (!cap.isOpened()) {
            scratchMat.release();
            throw new IngestionException("ingest", "Failed to open video source: " + source, null,
                    Map.of("path", source));
        }
        this.closed = false;
        this.producedFrames = 0L;
        this.firstFrame = true;
    }

    @Override
    public synchronized Optional<FrameEnvelope> next() throws IngestionException {
        if (closed) {
            return Optional.empty();
        }

        if (firstFrame) {
            firstFrame = false;
            if (!cap.grab() || !cap.retrieve(scratchMat) || scratchMat.empty()) {
                return Optional.empty();
            }
        } else {
            if (frameStride > 1) {
                for (int i = 0; i < frameStride - 1; i++) {
                    if (!cap.grab()) {
                        return Optional.empty();
                    }
                }
            }
            if (!cap.grab() || !cap.retrieve(scratchMat) || scratchMat.empty()) {
                return Optional.empty();
            }
        }

        long ts = (long) cap.get(Videoio.CAP_PROP_POS_MSEC);
        // Justification: newMat ownership is transferred to ManagedMat and returned in FrameEnvelope; caller closes envelope.
        Mat newMat = new Mat();
        scratchMat.copyTo(newMat);
        long index = producedFrames++;
        ManagedMat managed = new ManagedMat(newMat);
        return Optional.of(new FrameEnvelope(index, ts, managed, sourceId));
    }

    @Override
    public double fps() {
        return cap.get(Videoio.CAP_PROP_FPS);
    }

    @Override
    public int totalFrames() {
        return (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT);
    }

    @Override
    public String sourceId() {
        return sourceId;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            // Justification: Deterministic release of persistent native OpenCV resources (scratchMat and VideoCapture).
            scratchMat.release();
            cap.release();
        }
    }
}
