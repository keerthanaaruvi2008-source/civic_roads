package com.roaddamage.stage3;

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
 * Stage 3 fine-grained severity classifier using EfficientNet.
 * <p>
 * Thread-safety: Internal execution is synchronized with a {@link ReentrantLock} to guarantee
 * thread safety for single-caller pipeline invocation.
 */
public final class EfficientNetSeverityClassifier implements SeverityClassifier {

    private static final Logger LOGGER = System.getLogger(EfficientNetSeverityClassifier.class.getName());

    private final OrtSession session;
    private final String inputName;
    private final DirectFloatBufferPool pool;
    private final ReentrantLock lock;
    private final AtomicBoolean closed;

    /**
     * Constructs an EfficientNetSeverityClassifier, validating tensor shapes and initializing the session.
     *
     * @param modelPath path to the EfficientNet .onnx model file
     * @param pool direct float buffer pool for inference input
     * @throws InferenceException if model opening fails or tensor shapes do not match expected specifications
     * @throws NullPointerException if modelPath or pool is null
     */
    public EfficientNetSeverityClassifier(Path modelPath, DirectFloatBufferPool pool) throws InferenceException {
        this.pool = Objects.requireNonNull(pool, "pool must not be null");
        this.session = OrtRuntime.openSession(modelPath);
        this.lock = new ReentrantLock();
        this.closed = new AtomicBoolean(false);

        try {
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            if (inputInfo.size() != 1) {
                session.close();
                throw new InferenceException("stage3", "Expected exactly 1 input, found: " + inputInfo.size(), null,
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
                        || shape[2] != ModelConfig.STAGE3_INPUT_SIZE
                        || shape[3] != ModelConfig.STAGE3_INPUT_SIZE) {
                    session.close();
                    throw new InferenceException("stage3",
                            "Expected input shape [1, 3, 224, 224], found: " + Arrays.toString(shape), null,
                            Map.of("actualShape", Arrays.toString(shape)));
                }
            } else {
                session.close();
                throw new InferenceException("stage3", "Input info is not a TensorInfo: " + inVal);
            }

            Map<String, NodeInfo> outInfo = session.getOutputInfo();
            if (outInfo.isEmpty()) {
                session.close();
                throw new InferenceException("stage3", "No output info found in model");
            }

            Map.Entry<String, NodeInfo> outEntry = outInfo.entrySet().iterator().next();
            ValueInfo outVal = outEntry.getValue().getInfo();
            if (outVal instanceof TensorInfo outTensor) {
                long[] outShape = outTensor.getShape();
                if (outShape == null || outShape.length < 1 || outShape[outShape.length - 1] != 3) {
                    session.close();
                    throw new InferenceException("stage3",
                            "Expected output last dimension == 3, found: " + Arrays.toString(outShape), null,
                            Map.of("actualShape", Arrays.toString(outShape)));
                }
            } else {
                session.close();
                throw new InferenceException("stage3", "Output info is not a TensorInfo: " + outVal);
            }
        } catch (OrtException e) {
            try {
                session.close();
            } catch (OrtException ignored) {
                // Secondary close exception ignored
            }
            throw new InferenceException("stage3", "Failed inspecting EfficientNet model metadata: " + modelPath, e,
                    Map.of("path", modelPath.toString(), "error", e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    @Override
    public float[] classify(Mat bgrCrop) throws InferenceException {
        Objects.requireNonNull(bgrCrop, "bgrCrop must not be null");

        lock.lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("EfficientNetSeverityClassifier is closed");
            }

            int targetSize = ModelConfig.STAGE3_INPUT_SIZE;
            int requiredFloats = 3 * targetSize * targetSize;
            FloatBuffer buf = pool.acquire(requiredFloats);

            try {
                FrameConverter.toCHWFloat(bgrCrop, targetSize, ModelConfig.IMAGENET_MEAN, ModelConfig.IMAGENET_STD, buf);

                try (TensorLease in = TensorLease.wrap(OnnxTensor.createTensor(OrtRuntime.environment(), buf,
                             new long[]{1, 3, targetSize, targetSize}));
                     OrtSession.Result out = session.run(Map.of(inputName, in.tensor()))) {

                    Object val = out.get(0).getValue();
                    if (!(val instanceof float[][] arr) || arr.length < 1 || arr[0].length < 3) {
                        throw new InferenceException("stage3", "Unexpected output shape from severity classifier", null,
                                Map.of("output", val == null ? "null" : val.getClass().getName()));
                    }

                    float[] logits = arr[0];
                    float max = Math.max(logits[0], Math.max(logits[1], logits[2]));
                    float exp0 = (float) Math.exp(logits[0] - max);
                    float exp1 = (float) Math.exp(logits[1] - max);
                    float exp2 = (float) Math.exp(logits[2] - max);
                    float sum = exp0 + exp1 + exp2;

                    return new float[]{exp0 / sum, exp1 / sum, exp2 / sum};
                } catch (OrtException e) {
                    throw new InferenceException("stage3", "EfficientNet severity inference execution failed", e,
                            Map.of("inputName", inputName, "error", e.getMessage() == null ? "" : e.getMessage()));
                }
            } finally {
                pool.release(buf);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                session.close();
            } catch (OrtException e) {
                LOGGER.log(Level.WARNING, "Failed to cleanly close EfficientNet session: {0}", e.getMessage());
            }
        }
    }
}
