package com.roaddamage.pipeline;

import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.ManagedMat;
import com.roaddamage.infer.TensorLease;
import com.roaddamage.ingest.FrameSource;
import com.roaddamage.ingest.VideoFrameGrabber;
import com.roaddamage.ui.FrameSnapshot;
import javafx.application.Platform;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test validating pipeline execution against video files,
 * native memory leak audits, and frame accounting identities.
 */
final class PipelineEndToEndTest {

    private static final Path SAMPLES_DIR = Path.of("samples");
    private static final Path VIDEO_PATH = SAMPLES_DIR.resolve("short_road.mp4");

    @BeforeAll
    static void initEnvironment() throws IOException {
        OpenCV.loadLocally();
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX runtime already active
        }

        ensureSampleVideoExists(VIDEO_PATH);
    }

    /**
     * Generates a synthetic 640x480 test video if the sample file is absent.
     */
    static void ensureSampleVideoExists(Path path) throws IOException {
        if (Files.isRegularFile(path) && Files.size(path) > 0) {
            // Verify readability via VideoCapture
            VideoCapture cap = new VideoCapture(path.toString());
            boolean readable = cap.isOpened() && cap.read(new Mat());
            cap.release();
            if (readable) {
                return;
            }
        }

        Files.createDirectories(path.getParent());

        int width = 640;
        int height = 480;
        double fps = 30.0;
        int frameCount = 30;

        int[] codecs = {
                VideoWriter.fourcc('m', 'p', '4', 'v'),
                VideoWriter.fourcc('a', 'v', 'c', '1'),
                VideoWriter.fourcc('M', 'J', 'P', 'G'),
                VideoWriter.fourcc('X', 'V', 'I', 'D')
        };

        VideoWriter writer = null;
        for (int fourcc : codecs) {
            writer = new VideoWriter(path.toString(), fourcc, fps, new Size(width, height), true);
            if (writer.isOpened()) {
                break;
            }
            writer.release();
        }

        if (writer == null || !writer.isOpened()) {
            throw new IOException("Failed to initialize OpenCV VideoWriter for: " + path);
        }

        try {
            Mat frame = new Mat(height, width, CvType.CV_8UC3, new Scalar(50, 50, 50));
            for (int i = 0; i < frameCount; i++) {
                // Draw road markings
                Imgproc.line(frame, new Point(width / 2.0, 0), new Point(width / 2.0, height), new Scalar(0, 215, 255), 3);
                Imgproc.line(frame, new Point(80, 0), new Point(80, height), new Scalar(255, 255, 255), 2);
                Imgproc.line(frame, new Point(width - 80, 0), new Point(width - 80, height), new Scalar(255, 255, 255), 2);

                if (i >= 10 && i <= 20) {
                    Imgproc.circle(frame, new Point(320, 240), 30, new Scalar(15, 15, 15), -1);
                }
                writer.write(frame);
            }
            frame.release();
        } finally {
            writer.release();
        }
    }

    @Test
    @DisplayName("End-to-end execution against short_road.mp4 satisfies accounting identity and zero leaks")
    void endToEndPipelineRunAgainstSampleVideo() throws Exception {
        PipelineConfig config = new PipelineConfig(Path.of("models"), 1, 16, 0.40f, true);
        List<FrameResult> results = new CopyOnWriteArrayList<>();
        List<FrameSnapshot> snapshots = new CopyOnWriteArrayList<>();

        InspectionPipeline pipeline = new InspectionPipeline(config, results::add, snapshots::add);
        try (FrameSource source = new VideoFrameGrabber(VIDEO_PATH, config.frameStride())) {
            pipeline.run(source);
        }

        PipelineMetrics.Snapshot metrics = pipeline.metrics().snapshot();
        pipeline.close();

        // 3.a Accounting identity: framesOut == framesIn - framesSkippedStage2 - droppedFrames
        assertEquals(metrics.framesOut(),
                metrics.framesIn() - metrics.framesSkippedStage2() - metrics.droppedFrames(),
                "Accounting identity violated: framesOut != framesIn - framesSkippedStage2 - droppedFrames");

        // 3.b NativeLeakAudit.snapshot().openTensorLeases == 0 after close()
        NativeLeakAudit.Snapshot auditSnapshot = NativeLeakAudit.snapshot();
        assertEquals(0L, auditSnapshot.openTensorLeases(),
                "Open TensorLease instances leaked after pipeline close: " + auditSnapshot.openTensorLeases());

        // 3.c NativeLeakAudit.snapshot().liveManagedMats == 0 after close()
        assertEquals(0L, auditSnapshot.liveManagedMats(),
                "Live ManagedMat instances leaked after pipeline close: " + auditSnapshot.liveManagedMats());

        // 3.d Every FrameResult has stage timing values >= 0
        assertFalse(results.isEmpty(), "Pipeline should have produced at least one FrameResult");
        for (FrameResult r : results) {
            assertTrue(r.stage1Nanos() >= 0L, "Negative stage1 timing: " + r.stage1Nanos());
            assertTrue(r.stage2Nanos() >= 0L, "Negative stage2 timing: " + r.stage2Nanos());
            assertTrue(r.stage3Nanos() >= 0L, "Negative stage3 timing: " + r.stage3Nanos());
        }
    }
}
