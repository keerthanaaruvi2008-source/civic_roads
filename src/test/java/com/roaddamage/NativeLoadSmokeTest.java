package com.roaddamage;

import ai.onnxruntime.OrtEnvironment;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

/**
 * Smoke tests validating native layer loading and configuration immutability.
 */
final class NativeLoadSmokeTest {

    @BeforeAll
    static void loadNatives() {
        OpenCV.loadLocally();
    }

    @Test
    void openCvNativeLibraryLoadsAndCreatesMat() {
        Mat m = new Mat(4, 4, CvType.CV_8UC3, new Scalar(10, 20, 30));
        try {
            Assertions.assertEquals(4, m.rows());
            Assertions.assertEquals(3, m.channels());
            Assertions.assertFalse(Core.VERSION.isBlank());
        } finally {
            // OpenCV Mat does not implement AutoCloseable; manual release is required
            m.release();
        }
    }

    @Test
    void onnxRuntimeEnvironmentInitializes() throws Exception {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        Assertions.assertNotNull(env);
        Assertions.assertFalse(OrtEnvironment.getAvailableProviders().isEmpty());
    }

    @Test
    void modelConfigIsNotInstantiable() throws Exception {
        var ctor = com.roaddamage.config.ModelConfig.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var ex = Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance);
        Assertions.assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }

    @Test
    void exceptionContextIsImmutable() {
        var ctx = new java.util.HashMap<String, Object>();
        ctx.put("k", "v");
        var e = new com.roaddamage.core.error.InferenceException("stage2", "msg", null, ctx);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> e.context().put("x", 1));
    }
}
