package com.jaffh.alkahestdrops.client.compat.jei;

/**
 * Represents the screen-space box a mob's model covers when drawn at scale 1 (one pixel per block) with its origin
 * (feet) at 0, 0. The y-axis points down, as on screen, so the model's top has the lowest y.
 *
 * @param minX Left edge.
 * @param maxX Right edge, above {@code minX}.
 * @param minY Top edge.
 * @param maxY Bottom edge, above {@code minY}.
 */
record ModelBounds(float minX, float maxX, float minY, float maxY) {

    /**
     * Returns the width of the box.
     *
     * @return The width in blocks.
     */
    float width() {
        return maxX - minX;
    }

    /**
     * Returns the height of the box.
     *
     * @return The height in blocks.
     */
    float height() {
        return maxY - minY;
    }

    /**
     * Returns the horizontal center of the box.
     *
     * @return The center x, relative to the model's origin.
     */
    float centerX() {
        return (minX + maxX) / 2.0F;
    }

    /**
     * Returns the vertical center of the box.
     *
     * @return The center y, relative to the model's origin.
     */
    float centerY() {
        return (minY + maxY) / 2.0F;
    }

    /**
     * Computes the scale at which the model fits an area.
     *
     * @param width  Width of the area, in pixels.
     * @param height Height of the area, in pixels.
     * @return Pixels per block.
     */
    float scaleToFit(float width, float height) {
        return Math.min(width / width(), height / height());
    }
}
