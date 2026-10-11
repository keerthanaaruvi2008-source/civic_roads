package com.roaddamage.config;

/**
 * Global configuration constants for multi-stage inference models and pre-processing parameters.
 */
public final class ModelConfig {

    /** Model input dimension (height and width) for Stage 1 MobileNetV3 screening. */
    public static final int STAGE1_INPUT_SIZE = 224;

    /** Model input dimension (height and width) for Stage 2 YOLOv8 detection. */
    public static final int STAGE2_INPUT_SIZE = 640;

    /** Model input dimension (height and width) for Stage 3 EfficientNet severity scoring. */
    public static final int STAGE3_INPUT_SIZE = 224;

    /** Damage classification threshold for Stage 1. */
    public static final float STAGE1_DAMAGE_THRESHOLD = 0.50f;

    /** Confidence threshold for Stage 2 YOLOv8 bounding box proposals. */
    public static final float STAGE2_CONF_THRESHOLD = 0.25f;

    /** Non-Maximum Suppression (NMS) Intersection-over-Union (IoU) threshold for Stage 2. */
    public static final float STAGE2_NMS_IOU = 0.45f;

    /** ImageNet normalization channel mean values in RGB order. */
    public static final float[] IMAGENET_MEAN = {0.485f, 0.456f, 0.406f};

    /** ImageNet normalization channel standard deviation values in RGB order. */
    public static final float[] IMAGENET_STD = {0.229f, 0.224f, 0.225f};

    /** Number of intra-operator threads allocated for ONNX Runtime sessions. */
    public static final int ORT_INTRA_OP_THREADS = 4;

    /** Relative or absolute directory containing ONNX model artifacts. */
    public static final String MODEL_DIR = "models";

    // --- SEVERITY & PRIORITY ASSESSMENT CONFIGURATION ---

    /** Damage class severity base weight for longitudinal cracks (D00). */
    public static final float SEVERITY_CLASS_WEIGHT_D00 = 0.55f;

    /** Damage class severity base weight for transverse cracks (D10). */
    public static final float SEVERITY_CLASS_WEIGHT_D10 = 0.55f;

    /** Damage class severity base weight for alligator cracks (D20). */
    public static final float SEVERITY_CLASS_WEIGHT_D20 = 0.75f;

    /** Damage class severity base weight for potholes (D40). */
    public static final float SEVERITY_CLASS_WEIGHT_D40 = 1.00f;

    /** Relative image area ratio at or above which area score saturates to 1.0. */
    public static final float SEVERITY_AREA_SATURATION_RATIO = 0.05f;

    /** Aspect ratio threshold above which elongated cracks receive an aspect boost. */
    public static final float SEVERITY_ASPECT_CRACK_THRESHOLD = 4.0f;

    /** Additive aspect ratio boost for severe elongated cracks. */
    public static final float SEVERITY_ASPECT_BOOST = 0.10f;

    /** Priority scoring weight assigned to damage class hazard. */
    public static final float SEVERITY_WEIGHT_CLASS = 0.40f;

    /** Priority scoring weight assigned to relative surface area distress. */
    public static final float SEVERITY_WEIGHT_AREA = 0.30f;

    /** Priority scoring weight assigned to neural network model severity prediction. */
    public static final float SEVERITY_WEIGHT_MODEL = 0.30f;

    /** Pothole priority score threshold triggering critical alerts regardless of level. */
    public static final int SEVERITY_ALERT_POTHOLE_SCORE_THRESHOLD = 60;

    private ModelConfig() {
        throw new UnsupportedOperationException("Utility configuration class cannot be instantiated");
    }
}
