package com.roaddamage.stage2;

import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Class-aware Non-Maximum Suppression (NMS) algorithm for eliminating redundant overlapping detection proposals.
 */
public final class Nms {

    private Nms() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Executes class-aware Non-Maximum Suppression on detection candidates.
     * <p>
     * Detections are partitioned by {@link DamageClass}, sorted by confidence in descending order,
     * and suppressed if their Intersection-over-Union (IoU) with a higher-scoring proposal exceeds {@code iouThreshold}.
     * The retained detections across all classes are returned sorted by confidence descending.
     *
     * @param candidates list of candidate detections
     * @param iouThreshold intersection-over-union suppression threshold in [0.0, 1.0]
     * @return unmodifiable list of filtered detections sorted by confidence descending
     * @throws NullPointerException if candidates is null or contains null elements
     * @throws IllegalArgumentException if iouThreshold is outside [0.0, 1.0]
     */
    public static List<Detection> classAwareNms(List<Detection> candidates, float iouThreshold) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (iouThreshold < 0.0f || iouThreshold > 1.0f) {
            throw new IllegalArgumentException("iouThreshold must be in [0, 1]: " + iouThreshold);
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        Map<DamageClass, List<Detection>> byClass = new EnumMap<>(DamageClass.class);
        for (Detection d : candidates) {
            Objects.requireNonNull(d, "candidate detection must not be null");
            byClass.computeIfAbsent(d.damageClass(), k -> new ArrayList<>()).add(d);
        }

        List<Detection> retained = new ArrayList<>();

        for (List<Detection> classList : byClass.values()) {
            classList.sort((a, b) -> Float.compare(b.confidence(), a.confidence()));
            int n = classList.size();
            boolean[] suppressed = new boolean[n];

            for (int i = 0; i < n; i++) {
                if (suppressed[i]) {
                    continue;
                }
                Detection primary = classList.get(i);
                retained.add(primary);

                for (int j = i + 1; j < n; j++) {
                    if (!suppressed[j]) {
                        Detection candidate = classList.get(j);
                        if (primary.box().iou(candidate.box()) > iouThreshold) {
                            suppressed[j] = true;
                        }
                    }
                }
            }
        }

        retained.sort((a, b) -> Float.compare(b.confidence(), a.confidence()));
        return Collections.unmodifiableList(retained);
    }
}
