package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import com.jaffh.alkahestcore.dropscore.common.drops.DropNumbers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * A tooltip line that shows a drop chance as a bar, filled and colored by its {@link ChanceRarity}, followed by the
 * chance as text in the same color ("0.3% chance").
 *
 * @param chance Chance, 0–1.
 */
record ChanceBar(double chance) implements TooltipComponent {

    /**
     * Draws a {@link ChanceBar}: the bar, then the chance.
     */
    static final class Renderer implements ClientTooltipComponent {
        private static final int BAR_WIDTH = 50;
        private static final int BAR_HEIGHT = 6;
        private static final int GAP = 4;
        private static final int HEIGHT = 10;
        private static final int BAR_BORDER = 0xFF000000;
        private static final int BAR_EMPTY = 0xFF3A3A3A;
        private static final int OPAQUE = 0xFF000000;

        private final Component text;
        private final int color;
        private final int filled;

        /**
         * Creates the renderer of one bar.
         *
         * @param bar Bar to draw. Never null.
         */
        Renderer(ChanceBar bar) {
            double chance = Math.max(0.0, Math.min(1.0, bar.chance()));
            this.text = DropDisplayFormat.text("chance_value", DropNumbers.percent(chance));
            this.color = ChanceRarity.of(chance).color();
            this.filled = chance <= 0 ? 0 : Math.max(1, (int) Math.round(chance * (BAR_WIDTH - 2)));
        }

        /**
         * Returns the height of the line.
         *
         * @return The height in pixels.
         */
        @Override
        public int getHeight() {
            return HEIGHT;
        }

        /**
         * Returns the width of the line.
         *
         * @param font Tooltip font. Never null.
         * @return The width in pixels.
         */
        @Override
        public int getWidth(Font font) {
            return BAR_WIDTH + GAP + font.width(text);
        }

        /**
         * Draws the bar and the chance.
         *
         * @param font        Tooltip font. Never null.
         * @param x           Left edge.
         * @param y           Top edge.
         * @param guiGraphics Graphics. Never null.
         */
        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
            int barY = y + 1;
            guiGraphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, BAR_BORDER);
            guiGraphics.fill(x + 1, barY + 1, x + BAR_WIDTH - 1, barY + BAR_HEIGHT - 1, BAR_EMPTY);
            if (filled > 0) guiGraphics.fill(x + 1, barY + 1, x + 1 + filled, barY + BAR_HEIGHT - 1, OPAQUE | color);
            guiGraphics.drawString(font, text, x + BAR_WIDTH + GAP, y, color, true);
        }
    }
}
