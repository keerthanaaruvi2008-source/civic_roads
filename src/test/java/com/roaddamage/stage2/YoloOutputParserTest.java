package com.roaddamage.stage2;

import com.roaddamage.core.error.InferenceException;
import com.roaddamage.core.model.DamageClass;
import com.roaddamage.core.model.Detection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YoloOutputParserTest {

    @Test
    void parsesAnchorsAndFiltersByConfidence() throws Exception {
        int numClasses = 4;
        int numAnchors = 3;
        // Shape [1, 8, 3]
        float[][][] output = new float[1][8][numAnchors];

        // Anchor 0: valid detection, class 1 (D10_TRANSVERSE), conf 0.85
        output[0][0][0] = 320.0f; // cx
        output[0][1][0] = 320.0f; // cy
        output[0][2][0] = 100.0f; // w
        output[0][3][0] = 60.0f;  // h
        output[0][4][0] = 0.05f;  // class 0
        output[0][5][0] = 0.85f;  // class 1
        output[0][6][0] = 0.05f;  // class 2
        output[0][7][0] = 0.05f;  // class 3

        // Anchor 1: below threshold (conf 0.15 < 0.25)
        output[0][0][1] = 100.0f;
        output[0][1][1] = 100.0f;
        output[0][2][1] = 40.0f;
        output[0][3][1] = 40.0f;
        output[0][4][1] = 0.10f;
        output[0][5][1] = 0.15f;
        output[0][6][1] = 0.05f;
        output[0][7][1] = 0.05f;

        // Anchor 2: tiny degenerate box (area <= 1 px)
        output[0][0][2] = 200.0f;
        output[0][1][2] = 200.0f;
        output[0][2][2] = 0.5f;
        output[0][3][2] = 0.5f;
        output[0][7][2] = 0.90f; // class 3

        Letterbox.LetterboxTransform t = Letterbox.compute(640, 640, 640);
        List<Detection> parsed = YoloOutputParser.parse(output, t, 0.25f, numClasses);

        assertEquals(1, parsed.size(), "Only anchor 0 should pass confidence and area filters");
        Detection d = parsed.get(0);
        assertEquals(DamageClass.D10_TRANSVERSE, d.damageClass());
        assertEquals(0.85f, d.confidence());
        assertEquals(270.0f, d.box().x1(), 0.01f);
        assertEquals(290.0f, d.box().y1(), 0.01f);
        assertEquals(370.0f, d.box().x2(), 0.01f);
        assertEquals(350.0f, d.box().y2(), 0.01f);
    }

    @Test
    void rejectsInvalidTensorDimensions() {
        Letterbox.LetterboxTransform t = Letterbox.compute(640, 640, 640);

        // Wrong channel count: 7 instead of 8
        float[][][] wrongChannels = new float[1][7][100];
        InferenceException ex = assertThrows(InferenceException.class,
                () -> YoloOutputParser.parse(wrongChannels, t, 0.25f, 4));
        assertTrue(ex.getMessage().contains("expected [1,8,N], got [1,7,100]"));
    }

    @Test
    void parserMicroBenchmark200IterationsOn8400Anchors() throws Exception {
        int numClasses = 4;
        int numAnchors = 8400;
        float[][][] output = new float[1][8][numAnchors];

        for (int i = 0; i < numAnchors; i++) {
            output[0][0][i] = 100.0f + (i % 400); // cx
            output[0][1][i] = 100.0f + (i % 400); // cy
            output[0][2][i] = 30.0f;              // w
            output[0][3][i] = 30.0f;              // h
            int targetClass = i % numClasses;
            output[0][4 + targetClass][i] = (i % 50 == 0) ? 0.85f : 0.05f;
        }

        Letterbox.LetterboxTransform t = Letterbox.compute(640, 640, 640);

        // Warm up JIT compiler
        for (int i = 0; i < 50; i++) {
            YoloOutputParser.parse(output, t, 0.25f, numClasses);
        }

        int iterations = 200;
        long start = System.nanoTime();
        int totalDetections = 0;
        for (int i = 0; i < iterations; i++) {
            List<Detection> d = YoloOutputParser.parse(output, t, 0.25f, numClasses);
            totalDetections += d.size();
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = (elapsed / 1_000_000.0) / iterations;

        System.out.println("PARSER_BENCHMARK: avg " + String.format(java.util.Locale.US, "%.3f", avgMs)
                + " ms per parse over 200 iterations (detections=" + (totalDetections / iterations) + ")");
        assertTrue(avgMs < 5.0, "Parser throughput must be < 5.0 ms per parse, was: " + avgMs + " ms");
    }
}
