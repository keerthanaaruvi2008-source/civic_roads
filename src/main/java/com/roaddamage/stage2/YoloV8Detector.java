package com.roaddamage.stage2;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.ValueInfo;
import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.error.InferenceException;
import com.roaddamage.core.model.Detection;
import com.roaddamage.infer.DirectFloatBufferPool;
import com.roaddamage.infer.OrtRuntime;
import com.roaddamage.infer.TensorLease;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.opencv.core.Mat;

/**
 * Stage 2 road damage localized proposal detector using YOLOv8.
 * <p>
 * Thread-safety: Detection operations are synchronized internally using a {@link ReentrantLock}.
 * Single caller at a time is supported across all inference invocations.
 */
public final class YoloV8Detector implements AutoCloseable {

    private static final Logger LOGGER = System.getLogger(YoloV8Detector.class.getName());
    private static final int NUM_CLASSES = 4;

    private final OrtSession session;
    private final String inputName;
    private final String outputName;
    private final DirectFloatBufferPool pool;
    private final ReentrantLock lock;
    private final AtomicBoolean closed;

    /**
     * Detection outcome along with execution latency metrics.
     *
     * @param detections unmodifiable list of post-processed and NMS-filtered detections
     * @param inferenceNanos pure neural network execution time in nanoseconds
     * @param totalNanos total pipeline duration including letterboxing, inference, decoding, and NMS
     */
    public record Stage2DetectionResult(List<Detection> detections, long inferenceNanos, long totalNanos) {

        /**
         * Validates non-null detections and non-negative durations.
         */
        public Stage2DetectionResult {
            Objects.requireNonNull(detections, "detections must not be null");
            if (inferenceNanos < 0L || totalNanos < 0L) {
                throw new IllegalArgumentException("Timings must be non-negative");
            }
        }
    }

    /**
     * Constructs a YoloV8Detector, opening the ONNX session and validating tensor shapes.
     *
     * @param modelPath path to the YOLOv8 .onnx model file
     * @param pool direct float buffer pool for memory reuse
     * @throws InferenceException if the model cannot be opened or signatures do not match YOLOv8 specifications
     * @throws NullPointerException if modelPath or pool is null
     */
    public YoloV8Detector(Path modelPath, DirectFloatBufferPool pool) throws InferenceException {
        this.pool = Objects.requireNonNull(pool, "pool must not be null");
        this.session = OrtRuntime.openSession(modelPath);
        this.lock = new ReentrantLock();
        this.closed = new AtomicBoolean(false);

        try {
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            if (inputInfo.size() != 1) {
                session.close();
                throw new InferenceException("stage2", "Expected exactly 1 input, found: " + inputInfo.size(), null,
                        Map.of("inputCount", inputInfo.size()));
            }

            Map.Entry<String, NodeInfo> inEntry = inputInfo.entrySet().iterator().next();
            this.inputName = inEntry.getKey();
            ValueInfo inVal = inEntry.getValue().getInfo();
            if (inVal instanceof TensorInfo inTensor) {
                long[] shape = inTensor.getShape();
                if (shape == null || shape.length != 4
                        || (shape[0] != 1 && shape[0] != -1)
                        || shape[1] != 3
                        || shape[2] != ModelConfig.STAGE2_INPUT_SIZE
                        || shape[3] != ModelConfig.STAGE2_INPUT_SIZE) {
                    session.close();
                    throw new InferenceException("stage2",
                            "Expected input shape [1, 3, 640, 640], found: " + Arrays.toString(shape), null,
                            Map.of("actualShape", Arrays.toString(shape)));
                }
            } else {
                session.close();
                throw new InferenceException("stage2", "Input info is not a TensorInfo: " + inVal);
            }

            Map<String, NodeInfo> outInfo = session.getOutputInfo();
            if (outInfo.isEmpty()) {
                session.close();
                throw new InferenceException("stage2", "No output info found in model");
            }

            Map.Entry<String, NodeInfo> outEntry = outInfo.entrySet().iterator().next();
            this.outputName = outEntry.getKey();
            ValueInfo outVal = outEntry.getValue().getInfo();
            if (outVal instanceof TensorInfo outTensor) {
                long[] shape = outTensor.getShape();
                int expectedDim1 = 4 + NUM_CLASSES;
                if (shape == null || shape.length != 3 || shape[1] != expectedDim1) {
                    session.close();
                    throw new InferenceException("stage2",
                            "Expected output rank 3 with dim 1 = " + expectedDim1 + ", found: " + Arrays.toString(shape),
                            null, Map.of("actualShape", Arrays.toString(shape)));
                }
            } else {
                session.close();
                throw new InferenceException("stage2", "Output info is not a TensorInfo: " + outVal);
            }
        } catch (OrtException e) {
            try {
                session.close();
            } catch (OrtException ignored) {
                // Secondary close exception ignored
            }
            throw new InferenceException("stage2", "Failed inspecting YOLOv8 model signatures: " + modelPath, e,
                    Map.of("path", modelPath.toString(), "error", e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    /**
     * Executes object detection on the provided BGR image frame and returns detected road damages.
     * Does not mutate the source frame.
     *
     * @param bgrFrame OpenCV BGR image Mat
     * @return unmodifiable list of detected road damage locations and classes
     * @throws InferenceException if preprocessing, ONNX evaluation, or parsing fails
     */
    public List<Detection> detect(Mat bgrFrame) throws InferenceException {
        return detectWithMetrics(bgrFrame).detections();
    }

    /**
     * Executes object detection on the provided BGR image frame and returns detections with profiling metrics.
     *
     * @param bgrFrame OpenCV BGR image Mat
     * @return Stage2DetectionResult containing detections and timing breakdown
     * @throws InferenceException if preprocessing, ONNX evaluation, or parsing fails
     */
    public Stage2DetectionResult detectWithMetrics(Mat bgrFrame) throws InferenceException {
        Objects.requireNonNull(bgrFrame, "bgrFrame must not be null");

        lock.lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("YoloV8Detector is closed");
            }

            long totalStart = System.nanoTime();
            int targetSize = ModelConfig.STAGE2_INPUT_SIZE;
            int requiredFloats = 3 * targetSize * targetSize;
            FloatBuffer buf = pool.acquire(requiredFloats);

            try {
                Letterbox.LetterboxTransform transform = Letterbox.applyToFloatCHW(bgrFrame, targetSize, buf);

                long inferenceStart = System.nanoTime();
                float[][][] rawOutput;

                try (TensorLease in = TensorLease.wrap(OnnxTensor.createTensor(OrtRuntime.environment(), buf,
                             new long[]{1, 3, targetSize, targetSize}));
                     OrtSession.Result out = session.run(Map.of(inputName, in.tensor()))) {

                    Object val = out.get(0).getValue();
                    if (!(val instanceof float[][][] arr)) {
                        throw new InferenceException("stage2", "Unexpected output tensor type: "
                                + (val == null ? "null" : val.getClass().getName()));
                    }
                    rawOutput = arr;
                } catch (OrtException e) {
                    throw new InferenceException("stage2", "YOLOv8 inference execution failed", e,
                            Map.of("inputName", inputName, "error", e.getMessage() == null ? "" : e.getMessage()));
                }

                long inferenceNanos = System.nanoTime() - inferenceStart;

                List<Detection> candidates = YoloOutputParser.parse(rawOutput, transform,
                        ModelConfig.STAGE2_CONF_THRESHOLD, NUM_CLASSES);

                List<Detection> filtered = Nms.classAwareNms(candidates, ModelConfig.STAGE2_NMS_IOU);
                long totalNanos = System.nanoTime() - totalStart;

                LOGGER.log(Level.DEBUG, "Stage 2 detected {0} damages in {1} ns (inference: {2} ns)",
                        filtered.size(), totalNanos, inferenceNanos);

                return new Stage2DetectionResult(filtered, inferenceNanos, totalNanos);
            } finally {
                pool.release(buf);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the name of the model's output tensor.
     *
     * @return output tensor name
     */
    public String outputName() {
        return outputName;
    }

    /**
     * Closes the underlying ONNX Runtime session and releases native resources.
     * Subsequent calls to this method are idempotent.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                session.close();
            } catch (OrtException e) {
                LOGGER.log(Level.WARNING, "Failed to cleanly close YoloV8Detector OrtSession: {0}", e.getMessage());
            }
        }
    }
}
