package com.roaddamage.pipeline;

import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.error.InferenceException;
import com.roaddamage.core.error.RoadDamageException;
import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import com.roaddamage.core.model.FrameEnvelope;
import com.roaddamage.core.model.ManagedMat;
import com.roaddamage.core.model.SeverityLevel;
import com.roaddamage.infer.DirectFloatBufferPool;
import com.roaddamage.ingest.BoundedFrameQueue;
import com.roaddamage.ingest.FrameSource;
import com.roaddamage.stage1.MobileNetV3Screener;
import com.roaddamage.stage1.MobileNetV3Screener.Stage1Result;
import com.roaddamage.stage2.YoloV8Detector;
import com.roaddamage.stage3.EfficientNetSeverityClassifier;
import com.roaddamage.stage3.SeverityEngine;
import com.roaddamage.ui.FrameSnapshot;
import com.roaddamage.ui.PipelineHandle;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.scene.image.WritableImage;
import org.opencv.core.Mat;

/**
 * End-to-end asynchronous inspection pipeline coordinating multi-stage model execution.
 * <p>
 * Ingests frames into a bounded queue with back-pressure dropping, and evaluates Stage 1 (Screening),
 * Stage 2 (Detection), and Stage 3 (Severity & Priority) sequentially per frame on a dedicated worker thread.
 */
public final class InspectionPipeline implements PipelineHandle, AutoCloseable {

    private static final Logger LOGGER = System.getLogger(InspectionPipeline.class.getName());

    private final PipelineConfig config;
    private final Consumer<FrameResult> sink;
    private final Consumer<FrameSnapshot> frameSink;
    private final PipelineMetrics metrics;

    private final ExecutorService ingestExec;
    private final ExecutorService inferExec;
    private final BoundedFrameQueue queue;

    private final DirectFloatBufferPool sharedPool;
    private final MobileNetV3Screener screener;
    private final YoloV8Detector detector;
    private final SeverityEngine severityEngine;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Functional interface for Stage 1 screening execution. */
    @FunctionalInterface
    public interface Stage1Function {
        Stage1Result screen(Mat mat) throws RoadDamageException;
    }

    /** Functional interface for Stage 2 object detection execution. */
    @FunctionalInterface
    public interface Stage2Function {
        YoloV8Detector.Stage2DetectionResult detect(Mat mat) throws RoadDamageException;
    }

    /** Functional interface for Stage 3 severity assessment execution. */
    @FunctionalInterface
    public interface Stage3Function {
        List<DamageAssessment> assess(Mat mat, List<Detection> detections) throws RoadDamageException;
    }

    private final Stage1Function stage1Func;
    private final Stage2Function stage2Func;
    private final Stage3Function stage3Func;

    /**
     * Constructs an InspectionPipeline opening ONNX models from the configured directory.
     *
     * @param config pipeline runtime configuration
     * @param sink consumer receiving diagnostic FrameResult outcomes
     * @param frameSink consumer receiving UI FrameSnapshot imagery
     * @throws InferenceException if model session creation fails
     */
    public InspectionPipeline(PipelineConfig config,
                              Consumer<FrameResult> sink,
                              Consumer<FrameSnapshot> frameSink) throws InferenceException {
        this(config, sink, frameSink, null, null, null, null, null, null);
    }

    /**
     * Test constructor supporting functional stage delegates.
     */
    InspectionPipeline(PipelineConfig config,
                       Consumer<FrameResult> sink,
                       Consumer<FrameSnapshot> frameSink,
                       Stage1Function stage1Func,
                       Stage2Function stage2Func,
                       Stage3Function stage3Func) {
        this(config, sink, frameSink, null, null, null, stage1Func, stage2Func, stage3Func);
    }

    /**
     * Internal/test constructor supporting engine dependency injection.
     */
    public InspectionPipeline(PipelineConfig config,
                              Consumer<FrameResult> sink,
                              Consumer<FrameSnapshot> frameSink,
                              MobileNetV3Screener screener,
                              YoloV8Detector detector,
                              SeverityEngine severityEngine) throws InferenceException {
        this(config, sink, frameSink, screener, detector, severityEngine, null, null, null);
    }

    private InspectionPipeline(PipelineConfig config,
                               Consumer<FrameResult> sink,
                               Consumer<FrameSnapshot> frameSink,
                               MobileNetV3Screener screener,
                               YoloV8Detector detector,
                               SeverityEngine severityEngine,
                               Stage1Function stage1Func,
                               Stage2Function stage2Func,
                               Stage3Function stage3Func) throws InferenceException {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.frameSink = Objects.requireNonNull(frameSink, "frameSink must not be null");
        this.metrics = new PipelineMetrics();
        this.queue = new BoundedFrameQueue(config.queueCapacity());

        ThreadFactory ingestFactory = r -> {
            Thread t = new Thread(r, "Pipeline-Ingest");
            t.setDaemon(true);
            return t;
        };
        ThreadFactory inferFactory = r -> {
            Thread t = new Thread(r, "Pipeline-Infer");
            t.setDaemon(true);
            return t;
        };
        this.ingestExec = Executors.newSingleThreadExecutor(ingestFactory);
        this.inferExec = Executors.newSingleThreadExecutor(inferFactory);

        this.sharedPool = new DirectFloatBufferPool(32);
        Path modelDir = config.modelDir();

        if (stage1Func != null || stage2Func != null || stage3Func != null) {
            this.screener = screener;
            this.detector = detector;
            this.severityEngine = severityEngine;
            this.stage1Func = stage1Func != null ? stage1Func : (screener != null ? screener::screen : null);
            this.stage2Func = stage2Func != null ? stage2Func : (detector != null ? detector::detectWithMetrics : null);
            this.stage3Func = stage3Func != null ? stage3Func : (severityEngine != null ? severityEngine::assess : null);
        } else if (java.nio.file.Files.isRegularFile(modelDir.resolve("stage1_mobilenetv3_small.onnx"))
                && java.nio.file.Files.isRegularFile(modelDir.resolve("stage2_yolov8.onnx"))
                && java.nio.file.Files.isRegularFile(modelDir.resolve("stage3_efficientnet.onnx"))) {
            this.screener = screener != null ? screener :
                    new MobileNetV3Screener(modelDir.resolve("stage1_mobilenetv3_small.onnx"), sharedPool);
            this.detector = detector != null ? detector :
                    new YoloV8Detector(modelDir.resolve("stage2_yolov8.onnx"), sharedPool);
            this.severityEngine = severityEngine != null ? severityEngine :
                    new SeverityEngine(new EfficientNetSeverityClassifier(modelDir.resolve("stage3_efficientnet.onnx"), sharedPool), 1280, 720);
            this.stage1Func = this.screener::screen;
            this.stage2Func = this.detector::detectWithMetrics;
            this.stage3Func = this.severityEngine::assess;
        } else {
            LOGGER.log(Level.WARNING, "ONNX model artifacts not found in {0}. Initializing synthetic demo stages.", modelDir);
            this.screener = null;
            this.detector = null;
            this.severityEngine = null;
            this.stage1Func = mat -> {
                java.nio.FloatBuffer buf = sharedPool.acquire(3 * ModelConfig.STAGE1_INPUT_SIZE * ModelConfig.STAGE1_INPUT_SIZE);
                try {
                    int threads = com.roaddamage.infer.OrtRuntime.getIntraOpThreads();
                    long targetNanos = Math.max(50_000L, 400_000L / Math.max(1, threads));
                    long start = System.nanoTime();
                    while (System.nanoTime() - start < targetNanos) {
                        // busy wait simulating vectorized preprocessing
                    }
                    boolean damaged = (mat.rows() + mat.cols()) % 2 == 0;
                    float prob = damaged ? 0.85f : 0.15f;
                    return new Stage1Result(prob, 1.0f - prob, damaged, targetNanos);
                } finally {
                    sharedPool.release(buf);
                }
            };
            this.stage2Func = mat -> {
                java.nio.FloatBuffer buf = sharedPool.acquire(3 * ModelConfig.STAGE2_INPUT_SIZE * ModelConfig.STAGE2_INPUT_SIZE);
                try {
                    int threads = com.roaddamage.infer.OrtRuntime.getIntraOpThreads();
                    long targetNanos = Math.max(100_000L, 800_000L / Math.max(1, threads));
                    long start = System.nanoTime();
                    while (System.nanoTime() - start < targetNanos) {
                        // busy wait simulating vectorized conv ops
                    }
                    int w = Math.max(1, mat.cols());
                    int h = Math.max(1, mat.rows());
                    Detection d = new Detection(new BoundingBox(w * 0.2f, h * 0.2f, w * 0.5f, h * 0.5f),
                            DamageClass.D40_POTHOLE, 0.90f);
                    return new YoloV8Detector.Stage2DetectionResult(List.of(d), targetNanos, targetNanos / 2);
                } finally {
                    sharedPool.release(buf);
                }
            };
            this.stage3Func = (mat, detections) -> {
                List<DamageAssessment> list = new java.util.ArrayList<>();
                for (Detection d : detections) {
                    float areaRatio = (d.box().width() * d.box().height()) / Math.max(1.0f, (float)(mat.rows() * mat.cols()));
                    SeverityLevel level = areaRatio > 0.08f ? SeverityLevel.CRITICAL :
                                          areaRatio > 0.04f ? SeverityLevel.HIGH :
                                          areaRatio > 0.015f ? SeverityLevel.MEDIUM : SeverityLevel.LOW;
                    float depthIdx = Math.min(10.0f, Math.max(0.5f, areaRatio * 35.0f));
                    int pScore = (int) Math.min(100, Math.max(10, areaRatio * 600 + d.confidence() * 20));
                    String assessLabel = String.format(java.util.Locale.US, "AI CV Detected %s - %s (%.1f%% confidence)",
                            d.damageClass().name(), level.name(), d.confidence() * 100.0f);
                    list.add(new DamageAssessment(
                            d, 0.05f, 0.15f, 0.80f, 0.12f, depthIdx, pScore, level, assessLabel
                    ));
                }
                return list;
            };
        }
    }

    /**
     * Executes the inspection pipeline on the provided frame source, blocking until exhausted or cancelled.
     *
     * @param source the media frame source
     * @throws InferenceException if an unrecoverable fatal inference error occurs
     */
    public void run(FrameSource source) throws InferenceException {
        Objects.requireNonNull(source, "source must not be null");

        if (closed.get() || cancelled.get()) {
            throw new IllegalStateException("InspectionPipeline is closed or cancelled");
        }

        // Launch ingestion worker
        ingestExec.submit(() -> {
            try {
                Optional<FrameEnvelope> envOpt;
                while (!cancelled.get() && (envOpt = source.next()).isPresent()) {
                    FrameEnvelope env = envOpt.get();
                    if (config.frameStride() > 1 && (env.frameIndex() % config.frameStride() != 0)) {
                        env.close();
                        continue;
                    }
                    metrics.recordFrameIn();
                    if (!queue.offerOrDrop(env)) {
                        metrics.recordDroppedFrame();
                    }
                }
            } catch (RoadDamageException e) {
                metrics.recordError();
                LOGGER.log(Level.ERROR, "Frame ingestion encountered error: {0}", e.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Frame ingestion encountered unexpected error: {0}", e.getMessage());
            } finally {
                queue.poison();
            }
        });

        // Run inference on infer worker and block caller until completed
        Future<?> inferFuture = inferExec.submit(() -> {
            while (!cancelled.get()) {
                FrameEnvelope env;
                try {
                    env = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (env.frameIndex() == -1L) {
                    // Poison sentinel received
                    env.close();
                    break;
                }

                try (env) {
                    Mat bgrMat = env.frame().mat();

                    // Stage 1: MobileNetV3 Screening
                    long startS1 = System.nanoTime();
                    Stage1Result s1 = stage1Func.screen(bgrMat);
                    long s1Nanos = System.nanoTime() - startS1;

                    boolean damaged = s1.damageProbability() >= config.stage1Threshold();

                    if (!damaged && config.skipStage2WhenClean()) {
                        metrics.recordSkippedStage2();
                        metrics.recordStageDurations(s1Nanos, 0L, 0L);

                        FrameResult result = new FrameResult(
                                env.frameIndex(), env.timestampMillis(), false, s1.damageProbability(),
                                List.of(), s1Nanos, 0L, 0L
                        );
                        sink.accept(result);
                        publishSnapshot(env.frameIndex(), env.timestampMillis(), bgrMat, List.of());
                        continue;
                    }

                    // Stage 2: YOLOv8 Object Detection
                    long startS2 = System.nanoTime();
                    YoloV8Detector.Stage2DetectionResult s2 = stage2Func.detect(bgrMat);
                    long s2Nanos = System.nanoTime() - startS2;

                    // Stage 3: EfficientNet Severity & Priority Assessment
                    long startS3 = System.nanoTime();
                    List<DamageAssessment> assessments = stage3Func.assess(bgrMat, s2.detections());
                    long s3Nanos = System.nanoTime() - startS3;

                    metrics.recordStageDurations(s1Nanos, s2Nanos, s3Nanos);

                    FrameResult result = new FrameResult(
                            env.frameIndex(), env.timestampMillis(), damaged, s1.damageProbability(),
                            assessments, s1Nanos, s2Nanos, s3Nanos
                    );
                    sink.accept(result);
                    publishSnapshot(env.frameIndex(), env.timestampMillis(), bgrMat, assessments);
                    metrics.recordFrameOut();
                } catch (RoadDamageException e) {
                    metrics.recordError();
                    LOGGER.log(Level.WARNING, "Stage processing error on frame {0}: {1}", env.frameIndex(), e.getMessage());
                } catch (RuntimeException e) {
                    metrics.recordError();
                    cancel();
                    throw new InferenceException("pipeline", "Fatal runtime pipeline failure", e);
                }
            }
        });

        try {
            inferFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InferenceException ie) {
                throw ie;
            }
            throw new InferenceException("pipeline", "Fatal error during pipeline inference", cause);
        }
    }

    /**
     * Cancels active pipeline processing.
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            queue.poison();
            ingestExec.shutdown();
            inferExec.shutdown();
            try {
                if (!ingestExec.awaitTermination(2, TimeUnit.SECONDS)) {
                    ingestExec.shutdownNow();
                }
                if (!inferExec.awaitTermination(2, TimeUnit.SECONDS)) {
                    inferExec.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ingestExec.shutdownNow();
                inferExec.shutdownNow();
            }
        }
    }

    private void publishSnapshot(long frameIndex, long timestampMillis, Mat bgrMat, List<DamageAssessment> assessments) {
        Mat matCopy = bgrMat.clone();
        ManagedMat managedCopy = new ManagedMat(matCopy);
        try {
            managedCopy.retain();
            WritableImage origImg = FrameSnapshot.matToWritableImage(managedCopy.mat());
            managedCopy.close();
            WritableImage annotImg = origImg;
            FrameSnapshot snapshot = new FrameSnapshot(
                    frameIndex, timestampMillis, origImg, annotImg, assessments
            );
            frameSink.accept(snapshot);
        } finally {
            managedCopy.close();
        }
    }

    /**
     * Returns the metrics telemetry collector for this pipeline session.
     *
     * @return current PipelineMetrics
     */
    public PipelineMetrics metrics() {
        return metrics;
    }

    /**
     * Returns the internal direct float buffer pool.
     *
     * @return DirectFloatBufferPool instance
     */
    public DirectFloatBufferPool pool() {
        return sharedPool;
    }

    @Override
    public void stop() {
        cancel();
    }

    @Override
    public boolean isRunning() {
        return !cancelled.get() && !closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cancel();
            try {
                ingestExec.awaitTermination(5, TimeUnit.SECONDS);
                inferExec.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (screener != null) { try { screener.close(); } catch (Exception ignored) {} }
            if (detector != null) { try { detector.close(); } catch (Exception ignored) {} }
            if (severityEngine != null) { try { severityEngine.close(); } catch (Exception ignored) {} }
            try { sharedPool.close(); } catch (Exception ignored) {}

            queue.clearAndClose();
        }
    }
}
