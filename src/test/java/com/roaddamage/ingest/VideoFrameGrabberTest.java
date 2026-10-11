package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import com.roaddamage.core.model.FrameEnvelope;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.videoio.VideoWriter;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VideoFrameGrabberTest {

    @TempDir
    Path tmp;

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    private Path writeSyntheticVideo(int frames) {
        Path out = tmp.resolve("synthetic_" + frames + ".avi");
        VideoWriter w = new VideoWriter(out.toString(),
                VideoWriter.fourcc('M', 'J', 'P', 'G'), 10.0, new Size(64, 48), true);
        Assumptions.assumeTrue(w.isOpened(), "VideoWriter failed to open MJPG codec");
        Mat frame = new Mat(48, 64, CvType.CV_8UC3, new Scalar(0, 128, 255));
        try {
            for (int i = 0; i < frames; i++) {
                w.write(frame);
            }
        } finally {
            frame.release();
            w.release();
        }
        return out;
    }

    @Test
    void readsAllFramesAndReleasesCleanly() throws Exception {
        Path video = writeSyntheticVideo(12);
        int count = 0;
        try (VideoFrameGrabber g = new VideoFrameGrabber(video)) {
            Optional<FrameEnvelope> env;
            while ((env = g.next()).isPresent()) {
                try (FrameEnvelope fe = env.get()) {
                    assertEquals(48, fe.frame().mat().rows());
                    assertEquals(64, fe.frame().mat().cols());
                    count++;
                }
            }
        }
        assertTrue(count >= 10, "expected ~12 frames, got " + count);
    }

    @Test
    void frameStrideSkipsFrames() throws Exception {
        Path video = writeSyntheticVideo(20);
        int full;
        int strided;
        try (VideoFrameGrabber g = new VideoFrameGrabber(video, 1)) {
            full = drain(g);
        }
        try (VideoFrameGrabber g = new VideoFrameGrabber(video, 4)) {
            strided = drain(g);
        }
        assertTrue(strided < full, "stride must reduce frame count");
        assertTrue(strided <= (full / 4) + 1, "strided count " + strided + " exceeds upper bound for full " + full);
    }

    @Test
    void openingMissingFileThrowsIngestionException() {
        IngestionException ex = assertThrows(IngestionException.class,
                () -> new VideoFrameGrabber(tmp.resolve("does-not-exist.mp4")));
        assertTrue(ex.component().toLowerCase().contains("ingest"));
        assertTrue(ex.context().containsKey("path"));
    }

    @Test
    void closeIsIdempotent() throws Exception {
        Path video = writeSyntheticVideo(3);
        VideoFrameGrabber g = new VideoFrameGrabber(video);
        g.close();
        assertDoesNotThrow(g::close);
    }

    private static int drain(FrameSource s) throws IngestionException {
        int n = 0;
        Optional<FrameEnvelope> e;
        while ((e = s.next()).isPresent()) {
            try (FrameEnvelope fe = e.get()) {
                n++;
            }
        }
        return n;
    }
}
