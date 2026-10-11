package com.roaddamage.core.model;

import java.util.Objects;

/**
 * Axis-aligned bounding box representation with normalized or absolute pixel coordinates.
 *
 * @param x1 minimum x coordinate (left boundary)
 * @param y1 minimum y coordinate (top boundary)
 * @param x2 maximum x coordinate (right boundary)
 * @param y2 maximum y coordinate (bottom boundary)
 */
public record BoundingBox(float x1, float y1, float x2, float y2) {

    /**
     * Compact constructor validating that the right and bottom boundaries are greater than or equal to left and top.
     *
     * @throws IllegalArgumentException if x2 < x1 or y2 < y1
     */
    public BoundingBox {
        if (x2 < x1) {
            throw new IllegalArgumentException("x2 must be >= x1: x1=" + x1 + ", x2=" + x2);
        }
        if (y2 < y1) {
            throw new IllegalArgumentException("y2 must be >= y1: y1=" + y1 + ", y2=" + y2);
        }
    }

    /**
     * Returns the width of the bounding box.
     *
     * @return box width
     */
    public float width() {
        return x2 - x1;
    }

    /**
     * Returns the height of the bounding box.
     *
     * @return box height
     */
    public float height() {
        return y2 - y1;
    }

    /**
     * Returns the area of the bounding box.
     *
     * @return box area in square units
     */
    public float area() {
        return width() * height();
    }

    /**
     * Computes the Intersection-over-Union (IoU) metric against another bounding box.
     * Returns 0.0f if the union area is zero.
     *
     * @param other the other bounding box
     * @return IoU value in [0.0, 1.0]
     * @throws NullPointerException if other is null
     */
    public float iou(BoundingBox other) {
        Objects.requireNonNull(other, "other must not be null");

        float interX1 = Math.max(x1, other.x1);
        float interY1 = Math.max(y1, other.y1);
        float interX2 = Math.min(x2, other.x2);
        float interY2 = Math.min(y2, other.y2);

        float interW = Math.max(0.0f, interX2 - interX1);
        float interH = Math.max(0.0f, interY2 - interY1);
        float interArea = interW * interH;

        float unionArea = area() + other.area() - interArea;
        if (unionArea <= 0.0f) {
            return 0.0f;
        }
        return interArea / unionArea;
    }

    /**
     * Clips the bounding box coordinates to the rectangular bounds [0, 0, width, height].
     *
     * @param width maximum allowable width boundary
     * @param height maximum allowable height boundary
     * @return clipped BoundingBox
     * @throws IllegalArgumentException if width or height is negative
     */
    public BoundingBox clipTo(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must be non-negative: " + width + "x" + height);
        }

        float maxW = (float) width;
        float maxH = (float) height;

        float clippedX1 = Math.clamp(x1, 0.0f, maxW);
        float clippedY1 = Math.clamp(y1, 0.0f, maxH);
        float clippedX2 = Math.clamp(x2, clippedX1, maxW);
        float clippedY2 = Math.clamp(y2, clippedY1, maxH);

        return new BoundingBox(clippedX1, clippedY1, clippedX2, clippedY2);
    }
}
