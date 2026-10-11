package com.roaddamage.stage3;

import com.roaddamage.core.error.InferenceException;
import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.Detection;
import com.roaddamage.core.model.SeverityLevel;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * End-to-end Stage 3 assessment orchestrator synthesizing object detections, neural severity classifications,
 * geometric footprint analysis, and prioritization scoring.
 * <p>
 * Memory contract: Submat crop regions created during assessment are non-owning views referencing {@code bgrFrame}.
 * Callers must ensure the underlying {@code bgrFrame} remains allocated and valid for the duration of {@link #assess(Mat, List)}.
 */
public final class SeverityEngine implements AutoCloseable {

    private static final Logger LOGGER = System.getLogger(SeverityEngine.class.getName());

    private final SeverityClassifier classifier;
    private final int frameWidth;
    private final int frameHeight;

    /**
     * Constructs a SeverityEngine configured for a specific frame resolution and classifier model.
     *
     * @param classifier neural network severity classification model
     * @param frameWidth expected full video frame width in pixels
     * @param frameHeight expected full video frame height in pixels
     * @throws NullPointerException if classifier is null
     * @throws IllegalArgumentException if frameWidth or frameHeight is not positive
     */
    public SeverityEngine(SeverityClassifier classifier, int frameWidth, int frameHeight) {
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
        if (frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive: " + frameWidth + "x" + frameHeight);
        }
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    /**
     * Evaluates a collection of Stage 2 detections on the source image, returning prioritized damage assessments.
     *
     * @param bgrFrame source OpenCV frame in BGR format (must outlive this invocation)
     * @param detections list of proposals from Stage 2 detection
     * @return unmodifiable list of damage assessments sorted by priority score descending
     * @throws NullPointerException if bgrFrame or detections is null
     * @throws InferenceException if severity classification inference fails
     */
    public List<DamageAssessment> assess(Mat bgrFrame, List<Detection> detections) throws InferenceException {
        Objects.requireNonNull(bgrFrame, "bgrFrame must not be null");
        Objects.requireNonNull(detections, "detections must not be null");

        if (detections.isEmpty()) {
            return Collections.emptyList();
        }

        float frameArea = (float) frameWidth * (float) frameHeight;
        List<DamageAssessment> assessments = new ArrayList<>(detections.size());

        for (Detection detection : detections) {
            BoundingBox box = detection.box();
            float areaRatio = box.area() / frameArea;

            float w = box.width();
            float h = box.height();
            float aspectRatio = Math.max(w, h) / Math.max(1e-6f, Math.min(w, h));

            BoundingBox clipped = box.clipTo(frameWidth, frameHeight);
            if (clipped.area() < 4.0f) {
                LOGGER.log(Level.DEBUG, "Skipping tiny clipped proposal (< 4px area): {0}", clipped);
                continue;
            }

            int rx = Math.clamp((int) clipped.x1(), 0, frameWidth - 1);
            int ry = Math.clamp((int) clipped.y1(), 0, frameHeight - 1);
            int rw = Math.clamp(Math.max(1, (int) clipped.width()), 1, frameWidth - rx);
            int rh = Math.clamp(Math.max(1, (int) clipped.height()), 1, frameHeight - ry);

            Rect roi = new Rect(rx, ry, rw, rh);

            // Justification: Non-owning ROI submat header referencing parent bgrFrame; must not be released independently.
            // Parent bgrFrame manages native memory lifecycle.
            Mat crop = new Mat(bgrFrame, roi);

            float[] probs = classifier.classify(crop);
            float pLow = probs[0];
            float pMed = probs[1];
            float pHigh = probs[2];

            int priorityScore = PriorityScorer.score(detection.damageClass(), areaRatio, aspectRatio, pLow, pMed, pHigh);
            SeverityLevel level = SeverityLevel.fromScore(priorityScore);
            String explanation = PriorityScorer.explain(detection.damageClass(), areaRatio, aspectRatio, pLow, pMed, pHigh);

            assessments.add(new DamageAssessment(
                    detection, pLow, pMed, pHigh, areaRatio, aspectRatio, priorityScore, level, explanation));
        }

        assessments.sort((a, b) -> Integer.compare(b.priorityScore(), a.priorityScore()));
        return Collections.unmodifiableList(assessments);
    }

    /**
     * Closes the underlying severity classification model and associated runtime sessions.
     */
    @Override
    public void close() {
        try {
            classifier.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to cleanly close SeverityClassifier: {0}", e.getMessage());
        }
    }
}
