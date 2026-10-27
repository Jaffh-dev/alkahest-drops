package com.jaffh.alkahestdrops.client.compat.jei;

/**
 * Represents the panel of drop slots of a category: a grid of 18-pixel cells inside a one-pixel inset border.
 *
 * @param x       Left edge of the panel, relative to the recipe.
 * @param y       Top edge of the panel, relative to the recipe.
 * @param columns Number of columns, above 0.
 * @param rows    Number of rows, above 0.
 */
record DropGrid(int x, int y, int columns, int rows) {
    /** Size of one cell, the size of a vanilla slot background. */
    static final int CELL = 18;

    /**
     * Returns how many drops fit in the grid.
     *
     * @return Columns times rows.
     */
    int size() {
        return columns * rows;
    }

    /**
     * Returns the width of the panel.
     *
     * @return The cells plus the border.
     */
    int width() {
        return columns * CELL + 2;
    }

    /**
     * Returns the height of the panel.
     *
     * @return The cells plus the border.
     */
    int height() {
        return rows * CELL + 2;
    }

    /**
     * Returns the x of a cell's slot background.
     *
     * @param index Index of the cell, row by row.
     * @return The left edge of the cell.
     */
    int cellX(int index) {
        return x + 1 + (index % columns) * CELL;
    }

    /**
     * Returns the y of a cell's slot background.
     *
     * @param index Index of the cell, row by row.
     * @return The top edge of the cell.
     */
    int cellY(int index) {
        return y + 1 + (index / columns) * CELL;
    }

    /**
     * Finds the cell under a point.
     *
     * @param mouseX Point x, relative to the recipe.
     * @param mouseY Point y, relative to the recipe.
     * @return The index of the cell, or -1 outside the cells.
     */
    int cellAt(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - x - 1) / CELL);
        int row = (int) Math.floor((mouseY - y - 1) / CELL);
        if (column < 0 || column >= columns || row < 0 || row >= rows) return -1;
        return row * columns + column;
    }
}
