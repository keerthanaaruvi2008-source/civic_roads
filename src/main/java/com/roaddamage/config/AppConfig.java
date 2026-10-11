package com.roaddamage.config;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Properties;

/**
 * Runtime application settings loader reading from {@code /config/application.properties} with
 * resilient fallbacks to {@link ModelConfig} defaults.
 */
public final class AppConfig {

    private static final Logger LOGGER = System.getLogger(AppConfig.class.getName());
    private static final String CONFIG_RESOURCE = "/config/application.properties";

    private final int ortIntraOpThreads;
    private final int frameStride;
    private final int queueCapacity;
    private final float stage1Threshold;
    private final boolean skipStage2WhenClean;

    private AppConfig(int ortIntraOpThreads, int frameStride, int queueCapacity,
                      float stage1Threshold, boolean skipStage2WhenClean) {
        this.ortIntraOpThreads = ortIntraOpThreads;
        this.frameStride = frameStride;
        this.queueCapacity = queueCapacity;
        this.stage1Threshold = stage1Threshold;
        this.skipStage2WhenClean = skipStage2WhenClean;
    }

    /**
     * Loads application configuration from the classpath resource or applies defaults.
     *
     * @return initialized AppConfig instance
     */
    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed reading {0}, using default config: {1}", CONFIG_RESOURCE, e.getMessage());
        }

        int threads = getInt(props, "ORT_INTRA_OP_THREADS", ModelConfig.ORT_INTRA_OP_THREADS);
        int stride = getInt(props, "FRAME_STRIDE", 1);
        int queueCap = getInt(props, "QUEUE_CAPACITY", 16);
        float s1Thresh = getFloat(props, "STAGE1_DAMAGE_THRESHOLD", ModelConfig.STAGE1_DAMAGE_THRESHOLD);
        boolean skipStage2 = getBoolean(props, "SKIP_STAGE2_WHEN_CLEAN", true);

        return new AppConfig(threads, stride, queueCap, s1Thresh, skipStage2);
    }

    private static int getInt(Properties props, String key, int def) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return def;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float getFloat(Properties props, String key, float def) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return def;
        try {
            return Float.parseFloat(val.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean getBoolean(Properties props, String key, boolean def) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return def;
        return Boolean.parseBoolean(val.trim());
    }

    public int ortIntraOpThreads() { return ortIntraOpThreads; }
    public int frameStride() { return frameStride; }
    public int queueCapacity() { return queueCapacity; }
    public float stage1Threshold() { return stage1Threshold; }
    public boolean skipStage2WhenClean() { return skipStage2WhenClean; }
}
