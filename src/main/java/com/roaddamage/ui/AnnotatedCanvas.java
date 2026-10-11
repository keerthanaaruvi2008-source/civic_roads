package com.roaddamage.ui;

import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.Detection;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Custom JavaFX {@link Canvas} rendering video frames overlaid with color-coded damage assessments.
 * <p>
 * Does not retain OpenCV Mat instances; only stores the most recent UI snapshot for rendering.
 */
public final class AnnotatedCanvas extends Canvas {

    private static final Font LABEL_FONT = Font.font("Segoe UI", FontWeight.BOLD, 12.0);

    private FrameSnapshot lastSnapshot;
    private double scale = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;

    /**
     * Default constructor for FXML initialization.
     */
    public AnnotatedCanvas() {
        this(640, 480);
    }

    /**
     * Constructs an AnnotatedCanvas with specified initial dimensions.
     *
     * @param width canvas width
     * @param height canvas height
     */
    @SuppressWarnings("this-escape")
    public AnnotatedCanvas(double width, double height) {
        super(width, height);
        widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        heightProperty().addListener((obs, oldVal, newVal) -> redraw());
    }

    /**
     * Renders the given snapshot onto the canvas. Must be invoked on the JavaFX Application Thread.
     *
     * @param snap the frame snapshot to render
     */
    public void render(FrameSnapshot snap) {
        this.lastSnapshot = snap;
        redraw();
    }

    /**
     * Redraws the current frame snapshot with bounding box overlays.
     */
    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        double canvasW = getWidth();
        double canvasH = getHeight();

        gc.clearRect(0, 0, canvasW, canvasH);

        if (lastSnapshot == null || lastSnapshot.original() == null) {
            return;
        }

        WritableImage image = lastSnapshot.original();
        double imgW = image.getWidth();
        double imgH = image.getHeight();

        if (imgW <= 0 || imgH <= 0 || canvasW <= 0 || canvasH <= 0) {
            return;
        }

        this.scale = Math.min(canvasW / imgW, canvasH / imgH);
        double renderW = imgW * scale;
        double renderH = imgH * scale;
        this.offsetX = (canvasW - renderW) / 2.0;
        this.offsetY = (canvasH - renderH) / 2.0;

        // Draw source frame image scaled preserving aspect ratio
        gc.drawImage(image, offsetX, offsetY, renderW, renderH);

        // Render assessment overlays
        if (lastSnapshot.assessments() != null) {
            for (DamageAssessment assessment : lastSnapshot.assessments()) {
                Detection detection = assessment.detection();
                BoundingBox box = detection.box();

                double bx = offsetX + box.x1() * scale;
                double by = offsetY + box.y1() * scale;
                double bw = box.width() * scale;
                double bh = box.height() * scale;

                Color color = switch (assessment.level()) {
                    case LOW -> Color.web("#22c55e");       // Green
                    case MEDIUM -> Color.web("#eab308");    // Yellow
                    case HIGH -> Color.web("#f97316");      // Orange
                    case CRITICAL -> Color.web("#ef4444");  // Red
                };

                // Draw bounding box
                gc.setStroke(color);
                gc.setLineWidth(2.5);
                gc.strokeRect(bx, by, bw, bh);

                // Draw label tag
                String label = String.format(Locale.US, "%s %.0f%%",
                        detection.damageClass().label(),
                        detection.confidence() * 100.0f);

                gc.setFont(LABEL_FONT);
                gc.setFill(color);
                double textY = Math.max(14.0, by - 4.0);
                gc.fillText(label, bx, textY);
            }
        }
    }

    /**
     * Returns the scale factor applied during the last render pass.
     *
     * @return scale ratio
     */
    public double scale() {
        return scale;
    }

    /**
     * Returns the horizontal offset applied during the last render pass.
     *
     * @return x offset in canvas pixels
     */
    public double offsetX() {
        return offsetX;
    }

    /**
     * Returns the vertical offset applied during the last render pass.
     *
     * @return y offset in canvas pixels
     */
    public double offsetY() {
        return offsetY;
    }
}
