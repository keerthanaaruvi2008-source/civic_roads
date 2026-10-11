package com.roaddamage.infer;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.roaddamage.core.error.InferenceException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrtRuntimeTest {

    @Test
    void environmentIsSharedAndNonNull() {
        OrtEnvironment env1 = OrtRuntime.environment();
        OrtEnvironment env2 = OrtRuntime.environment();
        assertNotNull(env1);
        assertSame(env1, env2, "OrtEnvironment must be a shared singleton");
    }

    @Test
    void newSessionOptionsReturnsConfiguredInstance() {
        try (OrtSession.SessionOptions options = OrtRuntime.newSessionOptions()) {
            assertNotNull(options);
        }
    }

    @Test
    void openSessionOnMissingFileThrowsInferenceExceptionWithContext() {
        Path missingPath = Path.of("models", "non_existent_model_12345.onnx");
        InferenceException ex = assertThrows(InferenceException.class, () -> OrtRuntime.openSession(missingPath));
        assertEquals("stage1", ex.component());
        assertTrue(ex.context().containsKey("path"));
        assertEquals("model file missing or unreadable", ex.context().get("error"));
    }

    @Test
    void openSessionRejectsNullPath() {
        assertThrows(NullPointerException.class, () -> OrtRuntime.openSession(null));
    }
}
