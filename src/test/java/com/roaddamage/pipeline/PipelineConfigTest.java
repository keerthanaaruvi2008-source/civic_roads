package com.roaddamage.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipelineConfigTest {

    private static final Path TEST_DIR = Path.of("models");

    @Test
    @DisplayName("Defaults creates valid PipelineConfig matching framework specifications")
    void defaultsCreatesValidConfig() {
        PipelineConfig cfg = PipelineConfig.defaults(TEST_DIR);
        assertNotNull(cfg);
        assertEquals(TEST_DIR, cfg.modelDir());
        assertEquals(1, cfg.frameStride());
        assertEquals(16, cfg.queueCapacity());
        assertEquals(com.roaddamage.config.ModelConfig.STAGE1_DAMAGE_THRESHOLD, cfg.stage1Threshold(), 1e-5f);
        assertTrue(cfg.skipStage2WhenClean());
    }

    @Test
    @DisplayName("Validates queueCapacity within [1, 64]")
    void validatesQueueCapacity() {
        assertDoesNotThrow(() -> new PipelineConfig(TEST_DIR, 1, 1, 0.5f, true));
        assertDoesNotThrow(() -> new PipelineConfig(TEST_DIR, 1, 64, 0.5f, true));

        assertThrows(IllegalArgumentException.class,
                () -> new PipelineConfig(TEST_DIR, 1, 0, 0.5f, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineConfig(TEST_DIR, 1, 65, 0.5f, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineConfig(TEST_DIR, 1, -5, 0.5f, true));
    }

    @Test
    @DisplayName("Validates frameStride >= 1")
    void validatesFrameStride() {
        assertDoesNotThrow(() -> new PipelineConfig(TEST_DIR, 1, 16, 0.5f, true));
        assertDoesNotThrow(() -> new PipelineConfig(TEST_DIR, 10, 16, 0.5f, true));

        assertThrows(IllegalArgumentException.class,
                () -> new PipelineConfig(TEST_DIR, 0, 16, 0.5f, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineConfig(TEST_DIR, -2, 16, 0.5f, true));
    }

    @Test
    @DisplayName("Validates stage1Threshold in [0.0, 1.0]")
    void validatesStage1Threshold() {
        assertDoesNotThrow(() -> new PipelineConfig(TEST_DIR, 1, 16, 0.0f, true));
        assertDoesNotThrow(() -> new PipelineConfig(TEST_DIR, 1, 16, 1.0f, true));

        assertThrows(IllegalArgumentException.class,
                () -> new PipelineConfig(TEST_DIR, 1, 16, -0.01f, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PipelineConfig(TEST_DIR, 1, 16, 1.01f, true));
    }

    @Test
    @DisplayName("Rejects null modelDir")
    void rejectsNullModelDir() {
        assertThrows(NullPointerException.class,
                () -> new PipelineConfig(null, 1, 16, 0.5f, true));
    }
}
