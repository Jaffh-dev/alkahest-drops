package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * A tooltip line that starts with an item icon the size of the letters, such as an enchanted sword before "Has
 * Looting bonus". An icon that is hard to see on the dark tooltip (the dragon egg) can sit on an advancement
 * challenge frame.
 *
 * @param icon     Item drawn before the text. Never null.
 * @param text     Text of the line, with its own style. Never null.
 * @param isFramed Whether a gold challenge frame is drawn behind the icon.
 */
record IconLine(ItemStack icon, Component text, boolean isFramed) implements TooltipComponent {

    /**
     * Creates a line with an icon and no frame.
     *
     * @param icon Item drawn before the text. Never null.
     * @param text Text of the line, with its own style. Never null.
     */
    IconLine(ItemStack icon, Component text) {
        this(icon, text, false);
    }

    /**
     * Draws an {@link IconLine}: the item at half size (8 px, the height of the letters), on its frame when it has
     * one, then the text.
     */
    static final class Renderer implements ClientTooltipComponent {
        private static final int ITEM_SIZE = 16;
        private static final float ICON_SCALE = 0.5F;
        private static final int ICON_SIZE = (int) (ITEM_SIZE * ICON_SCALE);
        private static final int GAP = 2;
        private static final ResourceLocation FRAMES = new ResourceLocation("textures/gui/advancements/widgets.png");
        /** Challenge frame of a completed advancement, in the 256×256 widgets texture. */
        private static final int FRAME_U = 26;
        private static final int FRAME_V = 128;
        private static final int FRAME_TEXTURE_SIZE = 26;
        private static final int TEXTURE_SIZE = 256;
        /** Drawn size of the frame: the height of a text line. */
        private static final int FRAME_SIZE = 10;
        /** Height of a plain text line of a tooltip. */
        private static final int HEIGHT = 10;
        private static final int TEXT_COLOR = 0xFFFFFF;

        private final IconLine line;

        /**
         * Creates the renderer of one line.
         *
         * @param line Line to draw. Never null.
         */
        Renderer(IconLine line) {
            this.line = line;
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
            return iconWidth() + GAP + font.width(line.text());
        }

        /**
         * Returns the width the icon takes.
         *
         * @return The frame size for framed icons, the icon size otherwise.
         */
        private int iconWidth() {
            return line.isFramed() ? FRAME_SIZE : ICON_SIZE;
        }

        /**
         * Draws the icon and the text.
         *
         * @param font        Tooltip font. Never null.
         * @param x           Left edge.
         * @param y           Top edge.
         * @param guiGraphics Graphics. Never null.
         */
        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
            int iconOffset = (iconWidth() - ICON_SIZE) / 2;
            if (line.isFramed()) {
                guiGraphics.blit(FRAMES, x, y, FRAME_SIZE, FRAME_SIZE, FRAME_U, FRAME_V, FRAME_TEXTURE_SIZE,
                        FRAME_TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            }
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x + iconOffset, y + iconOffset, 0);
            guiGraphics.pose().scale(ICON_SCALE, ICON_SCALE, 1.0F);
            guiGraphics.renderFakeItem(line.icon(), 0, 0);
            guiGraphics.pose().popPose();
            guiGraphics.drawString(font, line.text(), x + iconWidth() + GAP, y, TEXT_COLOR, true);
        }
    }
}
