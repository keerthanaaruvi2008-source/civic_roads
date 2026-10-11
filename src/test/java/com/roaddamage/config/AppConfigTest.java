package com.roaddamage.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppConfigTest {

    @Test
    @DisplayName("AppConfig loads application.properties or falls back to ModelConfig defaults")
    void appConfigLoadsSuccessfully() {
        AppConfig cfg = AppConfig.load();
        assertNotNull(cfg);
        assertTrue(cfg.ortIntraOpThreads() >= 1);
        assertTrue(cfg.frameStride() >= 1);
        assertTrue(cfg.queueCapacity() >= 1 && cfg.queueCapacity() <= 64);
        assertTrue(cfg.stage1Threshold() >= 0.0f && cfg.stage1Threshold() <= 1.0f);
        assertTrue(cfg.skipStage2WhenClean());
    }
}
