package com.roaddamage.stage1;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.ValueInfo;
import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.error.InferenceException;
import com.roaddamage.infer.DirectFloatBufferPool;
import com.roaddamage.infer.OrtRuntime;
import com.roaddamage.infer.TensorLease;
import com.roaddamage.ingest.FrameConverter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.opencv.core.Mat;

/**
 * Stage 1 inference engine performing binary road damage screening using MobileNetV3-Small.
 * <p>
 * Thread-safety: {@link #screen(Mat)} may be called from one thread at a time and is internally
 * synchronized via a {@link ReentrantLock}. Public methods do not use the {@code synchronized} modifier.
 */
public final class MobileNetV3Screener implements AutoCloseable {

    private static final Logger LOGGER = System.getLogger(MobileNetV3Screener.class.getName());

    private final OrtSession session;
    private final String inputName;
    private final DirectFloatBufferPool pool;
    private final ReentrantLock lock;
    private final AtomicBoolean closed;

    /**
     * Immutable outcome of a Stage 1 screening inference evaluation.
     *
     * @param damageProbability estimated probability that the frame contains road damage [0, 1]
     * @param cleanProbability estimated probability that the frame is clean road [0, 1]
     * @param damaged true if damageProbability meets or exceeds {@link ModelConfig#STAGE1_DAMAGE_THRESHOLD}
     * @param inferenceNanos elapsed execution duration in nanoseconds for the model run and softmax evaluation
     */
    public record Stage1Result(float damageProbability, float cleanProbability, boolean damaged, long inferenceNanos) {

        /**
         * Compact constructor enforcing probability bounds and sum normalization.
         *
         * @throws IllegalArgumentException if probabilities are outside [0, 1], do not sum to 1 within 1e-3,
         *                                  or inferenceNanos is negative
         */
        public Stage1Result {
            if (damageProbability < 0.0f || damageProbability > 1.0f) {
                throw new IllegalArgumentException("damageProbability must be in [0, 1]: " + damageProbability);
            }
            if (cleanProbability < 0.0f || cleanProbability > 1.0f) {
                throw new IllegalArgumentException("cleanProbability must be in [0, 1]: " + cleanProbability);
            }
            if (Math.abs((damageProbability + cleanProbability) - 1.0f) > 1e-3f) {
                throw new IllegalArgumentException(
                        "Probabilities must sum within 1e-3 of 1.0, got: " + (damageProbability + cleanProbability));
            }
            if (inferenceNanos < 0L) {
                throw new IllegalArgumentException("inferenceNanos must be >= 0: " + inferenceNanos);
            }
        }
    }

    /**
     * Constructs a MobileNetV3Screener, opening the ONNX model session and verifying its tensor signature.
     *
     * @param modelPath filesystem path to the ONNX model file
     * @param pool reusable direct float buffer pool for inference input
     * @throws InferenceException if the model cannot be loaded or its input signature does not match [1, 3, 224, 224]
     */
    public MobileNetV3Screener(Path modelPath, DirectFloatBufferPool pool) throws InferenceException {
        this.pool = Objects.requireNonNull(pool, "pool must not be null");
        this.session = OrtRuntime.openSession(modelPath);
        this.lock = new ReentrantLock();
        this.closed = new AtomicBoolean(false);

        try {
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            if (inputInfo.size() != 1) {
                session.close();
                throw new InferenceException("stage1", "Expected exactly 1 input, found: " + inputInfo.size(), null,
                        Map.of("inputCount", inputInfo.size()));
            }

            Map.Entry<String, NodeInfo> entry = inputInfo.entrySet().iterator().next();
            this.inputName = entry.getKey();
            NodeInfo nodeInfo = entry.getValue();
            ValueInfo valueInfo = nodeInfo.getInfo();

            if (valueInfo instanceof TensorInfo tensorInfo) {
                long[] shape = tensorInfo.getShape();
                if (shape == null || shape.length != 4
                        || (shape[0] != 1 && shape[0] != -1)
                        || shape[1] != 3
                        || shape[2] != ModelConfig.STAGE1_INPUT_SIZE
                        || shape[3] != ModelConfig.STAGE1_INPUT_SIZE) {
                    session.close();
                    throw new InferenceException("stage1",
                            "Expected input shape [1, 3, 224, 224] (allow -1 at dim 0), but found " + Arrays.toString(shape),
                            null,
                            Map.of("actualShape", Arrays.toString(shape), "inputName", inputName));
                }
            } else {
                session.close();
                throw new InferenceException("stage1", "Input is not a TensorInfo: " + valueInfo, null,
                        Map.of("inputInfo", String.valueOf(valueInfo)));
            }
        } catch (OrtException e) {
            try {
                session.close();
            } catch (OrtException ignored) {
                // Ignore secondary close exception
            }
            throw new InferenceException("stage1", "Failed inspecting input metadata for model: " + modelPath, e,
                    Map.of("path", modelPath.toString(), "error", e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    /**
     * Evaluates a BGR OpenCV frame through the Stage 1 MobileNetV3 screening model.
     *
     * @param bgrFrame the input frame in BGR color format
     * @return screening outcome containing classification probabilities and decision
     * @throws NullPointerException if bgrFrame is null
     * @throws IllegalStateException if this screener has been closed
     * @throws InferenceException if preprocessing or ONNX runtime evaluation fails
     */
    public Stage1Result screen(Mat bgrFrame) throws InferenceException {
        Objects.requireNonNull(bgrFrame, "bgrFrame must not be null");

        lock.lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("MobileNetV3Screener is closed");
            }

            int requiredFloats = 3 * ModelConfig.STAGE1_INPUT_SIZE * ModelConfig.STAGE1_INPUT_SIZE;
            FloatBuffer buf = pool.acquire(requiredFloats);

            try {
                FrameConverter.toCHWFloat(bgrFrame, ModelConfig.STAGE1_INPUT_SIZE,
                        ModelConfig.IMAGENET_MEAN, ModelConfig.IMAGENET_STD, buf);

                long startNanos = System.nanoTime();
                float damageProb;
                float cleanProb;

                try (TensorLease in = TensorLease.wrap(OnnxTensor.createTensor(OrtRuntime.environment(), buf,
                             new long[]{1, 3, ModelConfig.STAGE1_INPUT_SIZE, ModelConfig.STAGE1_INPUT_SIZE}));
                     OrtSession.Result out = session.run(Map.of(inputName, in.tensor()))) {

                    Object val = out.get(0).getValue();
                    if (!(val instanceof float[][] logits) || logits.length < 1 || logits[0].length < 2) {
                        throw new InferenceException("stage1", "Unexpected output shape from Stage 1 screener", null,
                                Map.of("output", val == null ? "null" : val.getClass().getName()));
                    }

                    float l0 = logits[0][0];
                    float l1 = logits[0][1];
                    float max = Math.max(l0, l1);
                    float exp0 = (float) Math.exp(l0 - max);
                    float exp1 = (float) Math.exp(l1 - max);
                    float sum = exp0 + exp1;
                    cleanProb = exp0 / sum;
                    damageProb = exp1 / sum;
                } catch (OrtException e) {
                    throw new InferenceException("stage1", "Failed executing Stage 1 inference", e,
                            Map.of("inputName", inputName, "error", e.getMessage() == null ? "" : e.getMessage()));
                }

                long elapsedNanos = System.nanoTime() - startNanos;
                boolean damaged = damageProb >= ModelConfig.STAGE1_DAMAGE_THRESHOLD;

                LOGGER.log(Level.DEBUG, "Stage 1 evaluated frame in {0} ns (damaged={1}, damageProb={2})",
                        elapsedNanos, damaged, damageProb);

                return new Stage1Result(damageProb, cleanProb, damaged, elapsedNanos);
            } finally {
                pool.release(buf);
            }
        } finally {
            lock.unlock();
        }
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
                LOGGER.log(Level.WARNING, "Failed to cleanly close OrtSession: {0}", e.getMessage());
            }
        }
    }
}
