package com.roaddamage.infer;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.roaddamage.config.ModelConfig;
import com.roaddamage.core.error.InferenceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Singleton-style runtime manager for ONNX Runtime environment and session life-cycle.
 */
public final class OrtRuntime {

    private OrtRuntime() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    private static final class EnvironmentHolder {
        private static final OrtEnvironment INSTANCE = OrtEnvironment.getEnvironment();
    }

    /**
     * Returns the lazily initialized, application-wide shared {@link OrtEnvironment}.
     *
     * @return active OrtEnvironment
     */
    public static OrtEnvironment environment() {
        return EnvironmentHolder.INSTANCE;
    }

    private static final java.util.concurrent.atomic.AtomicInteger CONFIGURED_INTRA_OP_THREADS =
            new java.util.concurrent.atomic.AtomicInteger(-1);

    /**
     * Overrides the intra-op thread count for subsequently opened ONNX sessions.
     *
     * @param threads thread count (>= 1)
     */
    public static void setIntraOpThreads(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1: " + threads);
        }
        CONFIGURED_INTRA_OP_THREADS.set(threads);
    }

    /**
     * Resets intra-op thread count to standard configuration.
     */
    public static void resetIntraOpThreads() {
        CONFIGURED_INTRA_OP_THREADS.set(-1);
    }

    /**
     * Resolves the current intra-op thread count from configuration or override.
     *
     * @return active thread count
     */
    public static int getIntraOpThreads() {
        int configured = CONFIGURED_INTRA_OP_THREADS.get();
        if (configured > 0) {
            return configured;
        }
        String prop = System.getProperty("com.roaddamage.ort.threads");
        if (prop != null && !prop.isBlank()) {
            try {
                int parsed = Integer.parseInt(prop.trim());
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {}
        }
        return ModelConfig.ORT_INTRA_OP_THREADS;
    }

    /**
     * Creates a new {@link OrtSession.SessionOptions} pre-configured with the thread counts
     * defined in {@link #getIntraOpThreads()}.
     *
     * @return configured SessionOptions
     * @throws IllegalStateException if options configuration fails
     */
    public static OrtSession.SessionOptions newSessionOptions() {
        return newSessionOptions(getIntraOpThreads());
    }

    /**
     * Creates a new {@link OrtSession.SessionOptions} pre-configured with the specified thread count.
     *
     * @param intraOpNumThreads thread count (>= 1)
     * @return configured SessionOptions
     * @throws IllegalStateException if options configuration fails
     */
    public static OrtSession.SessionOptions newSessionOptions(int intraOpNumThreads) {
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(intraOpNumThreads);
            return options;
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to configure OrtSession.SessionOptions", e);
        }
    }

    /**
     * Validates and opens an ONNX runtime session for the specified model file path.
     *
     * @param modelPath filesystem path to the .onnx model file
     * @return opened OrtSession
     * @throws InferenceException if the model file is unreadable or session creation fails
     */
    public static OrtSession openSession(Path modelPath) throws InferenceException {
        Objects.requireNonNull(modelPath, "modelPath must not be null");
        if (!Files.isReadable(modelPath)) {
            throw new InferenceException("stage1", "model file missing or unreadable", null,
                    Map.of("path", modelPath.toString(), "error", "model file missing or unreadable"));
        }

        try (OrtSession.SessionOptions options = newSessionOptions()) {
            return environment().createSession(modelPath.toString(), options);
        } catch (OrtException e) {
            throw new InferenceException("stage1", "Failed to open ONNX session for model: " + modelPath, e,
                    Map.of("path", modelPath.toString(), "error", e.getMessage() == null ? "" : e.getMessage()));
        }
    }
}
