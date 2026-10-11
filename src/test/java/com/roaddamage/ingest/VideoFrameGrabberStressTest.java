package com.roaddamage.ingest;

import com.roaddamage.core.model.FrameEnvelope;
import com.roaddamage.core.model.ManagedMat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VideoFrameGrabberStressTest {

    @TempDir
    Path tmp;

    @BeforeAll
    static void load() {
        OpenCV.loadLocally();
    }

    @Test
    void stressMicroBenchmark300FramesReleasesAllMats() throws Exception {
        Path videoPath = tmp.resolve("stress_300.avi");
        int targetFrames = 300;

        VideoWriter writer = new VideoWriter(videoPath.toString(),
                VideoWriter.fourcc('M', 'J', 'P', 'G'), 30.0, new Size(32, 24), true);
        Assumptions.assumeTrue(writer.isOpened(), "VideoWriter failed to open for stress test");

        Mat frame = new Mat(24, 32, CvType.CV_8UC3, new Scalar(50, 100, 150));
        try {
            for (int i = 0; i < targetFrames; i++) {
                writer.write(frame);
            }
        } finally {
            frame.release();
            writer.release();
        }

        List<FrameEnvelope> trackedEnvelopes = new ArrayList<>(targetFrames);
        int readCount = 0;

        long baselineActiveMats = ManagedMat.activeCount();

        try (VideoFrameGrabber grabber = new VideoFrameGrabber(videoPath)) {
            Optional<FrameEnvelope> envOpt;
            while ((envOpt = grabber.next()).isPresent()) {
                FrameEnvelope env = envOpt.get();
                trackedEnvelopes.add(env);
                readCount++;
                // Release the envelope immediately to test frame-by-frame pipeline lifecycle
                env.close();
                assertTrue(env.frame().isReleased(), "Frame Mat must be released immediately upon close");
            }
        }

        assertTrue(readCount >= 280, "Expected ~300 frames from grabber, got " + readCount);
        for (FrameEnvelope env : trackedEnvelopes) {
            assertTrue(env.frame().isReleased(), "All ingested frame Mats must be released after loop");
        }
        assertEquals(0, ManagedMat.activeCount() - baselineActiveMats,
                "Native Mat counter (via ManagedMat.activeCount) must return to zero after close");
    }
}
